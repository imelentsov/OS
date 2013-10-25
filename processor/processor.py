#!/usr/bin/python
# -*- coding: utf-8 -*-

"""
Processor help
Intellectual property of Ivan Melentsov
Usage:
	-f | --file <FileName>, programm to execute
	-h | --help, background information
    -c | --config <BitMask> flags initializeing
Python version: 3.3.2(and compatible)
"""


'''
Created on 2013-10-13 21:46
@summary: Processor, nothing more
@author: i.melentsov
'''

import getopt
import sys
import codecs


class Processor:

    MAX_NUM_IN2BYTES = 65535

    def __mod(num):
        return num & Processor.MAX_NUM_IN2BYTES

    def __init__(self):
        '''
        @summary: 
          Флаги: 

          флаг|провод|описание

          Выставляются на этапе декодирования:
          0  0   пуск
          1  1   зам1
          2  2   зам2, всегда 1
          3  3   чист
          4  4   выб
          5  5   запп

          6  6   оп - неудобно делать флагом (может принимать 5 значения), но как провод его можно рвать (идет нолик)
          Выставляются на этапе исполнения:
          7  7   перех
          8  8   больше нуля, выставляется как результат АЛУ при выставленном зам1
          9  9   ноль, выставляется как результат АЛУ
               
          итого: 10 настраеваемых флагов
               
          10  10  Ф - всегда ноль
            
            #########################
            есть на схеме но не учитывается(всегда 0):
          -  -  взап1 
             
          итого: 10 флагов, 11 проводов всего
        '''
        self.memory = [0] * (Processor.MAX_NUM_IN2BYTES + 1)   # 65536 - размер памяти в байтах
        self.ax = [0] * 2                                      # Рон
        self.mem_offset = 0                                    # смещение по памяти - адрес текущей команды (не регистр) 
        self.si = [0] * 2                                      # индексный регистр
        self.flags_config = [True] * 10 + [False] * 1          # состояние проводов
        self.flags = [False] * 11                              # реальное значение флгов будет найдено как: flag & flag_config
        self.op = 0
        self.operation_Code = 0
        self.operand_adr = 0

        self.signed_ax = lambda ax : Processor.__mod(-ax) if (not (self.__getFlag(8) or self.__getFlag(9))) else ax
        self.alu = {
            0   : lambda first, second: self.signed_ax(first),
            1   : lambda first, second: second,
            2   : lambda first, second: Processor.__mod(self.signed_ax(first) + second), 
            3   : lambda first, second: Processor.__mod(self.signed_ax(first) - second), 
            0xF : lambda first, second: 0
        }
        

    def setConfFlags(self, flags):
        for index, val in enumerate(flags[0:10]):
            self.flags_config[index] = False if val == '0' else True

    def __getFlag(self, num):
        return self.flags[num] & self.flags_config[num]

    def __getOperation(self):
        return self.op if(self.flags_config[6]) else 0

    def __setIp(self):
        '''
        @summary: сменить ip
        '''
        if(self.__getFlag(7)): # перех
            self.mem_offset = self.operand_adr
            # print("!!!{0:0x4}!!!".format(self.mem_offset))
        else:
            self.mem_offset += 3

    def __loadCom(self):
        '''
        @summary: загрузить комманду
        '''
        self.operation_Code = self.memory[self.mem_offset]
        self.operand_adr = Processor.__mod(((self.memory[self.mem_offset + 1] + self.si[0]) << 8) + self.memory[self.mem_offset + 2] + self.si[1])
        # адрес ячейки памяти с которой лежит операнд (2 байта),
        #  или адрес куда писать в память 
        #  или адрес откуда брать следующую комманду 

    def __decodeCom(self):
        '''
        @summary: выставить флаги
        '''
        for i in range(0,7):
            self.flags[i] = False

        self.op        = (self.operation_Code & 0xF0 ) >> 4 # первый полубайт КОП
        low_half_byte  = self.operation_Code & 0x0F         # второй полубайт КОП
        i = 0
        p = 4
        if(self.op != 0xF):
            i          = (low_half_byte & 0b0100) >> 2
            p          = low_half_byte & 0b11

        self.flags[0] = self.operation_Code != 0xFF # пуск
        self.flags[1] = p == 1                      # зам1
        self.flags[2] = True                        # зам2
        self.flags[3] = p != 2                      # чист
        self.flags[4] = i == 1                      # выб
        self.flags[5] = p == 0                      # запп
        self.flags[7] = (self.operation_Code == 0xFE or (self.operation_Code == 0xF0 and self.__getFlag(9)) or
                         self.operation_Code == 0xF4 or (self.operation_Code == 0xF1 and self.__getFlag(8)))



    def __execCom(self):
        first_operand_to_alu = (self.ax[0] << 8) + self.ax[1]
        second_operand_to_alu = self.operand_adr if (self.__getFlag(4)) \
                    else (self.memory[self.operand_adr] << 8) + self.memory[self.operand_adr + 1]

        alu_res = self.alu[self.__getOperation()](first_operand_to_alu, second_operand_to_alu)
        
        up_res  = (alu_res & 0xFF00) >> 8
        law_res = alu_res & 0x00FF

        if(self.__getFlag(5)): # запп
            self.memory[self.operand_adr]     = up_res
            self.memory[self.operand_adr + 1] = law_res

        if(self.__getFlag(1)): # зам1
            # рез в РОН
            self.ax[0] = up_res
            self.ax[1] = law_res
            # выставить пр
            self.flags[8] = alu_res > 0
            self.flags[9] = alu_res == 0

        if(self.__getFlag(2)): # зам2
            if(self.__getFlag(3)): # чист
                up_res  = 0
                law_res = 0
            self.si[0] = up_res
            self.si[1] = law_res
        self.__setIp()

    def getDump(self):
        print ("""
    ip : {ip:{width}{base}}
    ax : {ax:{width}{base}}
    si : {si:{width}{base}}""".format(
            ip = self.mem_offset,
            ax = (self.ax[0] << 8)+ self.ax[1],
            si = (self.si[0] << 8)+ self.si[1],
            base = 'X', width='04' 
            ))
        for raw in range(0, 4096):
            print("{0:04X}".format(raw << 4), end=": ")
            for column in range(0, 8):
                print("{0:02X}{1:02X}".format(self.memory[(raw << 4) + (column << 1)], self.memory[(raw << 4) + (column << 1) + 1]), end=" ")
            print()

    def run(self):
        while (True):
            self.__loadCom()
            self.__decodeCom()
            # проверить флаг пуск
            if(not self.__getFlag(0)):
                break
            self.__execCom()

            
    def readProg(self, file_prog):
        with codecs.open(file_prog, 'r') as f:
            line = f.readline()
            offset = 0
            line_index = 0
            while (line != ''):
                line_index += 1
                line = line.split(';')[0] # remove comments
                commands = line.split()
                if(commands.__len__() == 0):
                    line = f.readline()
                    continue
                if(offset == 65536):
                    print ("Man what a fuck are you doing? You program is too large for me. Dieing...")
                    sys.exit(2)
                for command in commands:
                    if (command.__len__() & 1):
                        print ("Incorrect sequence of codes: {seq}. In line {line}.".format(seq=command, line=line_index))
                        sys.exit(2)
                    _from = 0
                    _to = 2
                    while(_to <= command.__len__()):
                        self.memory[offset] = int(command[_from : _to], 16)
                        offset +=1
                        _from += 2
                        _to += 2
                line = f.readline()

def main():
    file_prog = ''
    flags_config =  ''
    try:
        options, remainder = getopt.getopt(sys.argv[1:], "hf:c:", ["help", "file=", "config="])
    except getopt.GetoptError as err:
        # print help information and exit:
        print(str(err))
        print(__doc__)
        sys.exit(2)
    for opt, arg in options:
        if opt in ('-f', '--file'):
            file_prog = arg
        elif opt in ('-c', '--config'):
            flags_config = arg
        elif opt in ('-h', '--help'):
            print(__doc__)
            sys.exit()
        else:
            assert False, "Unhandled option"
    if(file_prog == '' and remainder.__len__() > 0):
        file_prog = remainder[0]
    if(file_prog == ''):
        print("Program to execute not specified.")
        sys.exit(2)
    processor = Processor()
    processor.setConfFlags(flags_config)
    processor.readProg(file_prog)
    processor.run()
    processor.getDump()

if __name__ == '__main__':
    main()
