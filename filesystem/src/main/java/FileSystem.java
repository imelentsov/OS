import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Scanner;
import java.util.Set;

import org.apache.commons.lang3.ArrayUtils;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/**
 * <h1>
 * Определения:
 * </h1>
 * <p>
 * <b>Кластер</b> - вся адресуемая память ФС побита на блоки по {@link #BLOCK_SIZE} байт,</br>
 *  каждый такой блок и называется кластер (кластеры номируются с 1)
 * </p> 
 * <h1>
 * Описание структуры ФС:
 * </h1>
 * <ol>
 * <li>
 * 	256 байт задают множество свободных кластеров в виде отрезков [fs[i]; fs[i + k]] 
 * 	<ul>
 *  	<li>fs[i] - номер первого свободого кластера в очередной последовательности свободных кластеров,</li>
 *  	<li>fs[i + k] - последний свободный кластер в этой последовательности</br>
 *  		 <i>(т.е. в последовательности k + 1 - кластер)</i></li>
 *  	<li>если 255-ый кластер занят, то последним отрезком добавляется отрезок - [0, 0],</br>
 *  		 иначе правым концом последнего отрезка будет 255</br>
 *  		 <i>(занятость гарантирует что не вылезим за 256 байт - с занятами кластерами такая фишка не пройдет)</i>
 *  	</li>
 * 	</ul>
 * </li>
 * <li>1 байт - число файлов в ФС</li>
 * <li>
 * {@link #BLOCK_NUM} блоков по 128 байт задающих файлы в ФС</br>
 * <ul> 
 * <li>первые 127 байт задают имя файла (1 байт - длина имени, 126 байт - под имя)</li>
 * <li>1 байт - номер первого кластера файла</li>
 * </ul>
 * </li>
 * <li>
 * {@link #BLOCK_NUM} кластеров по {@link #BLOCK_SIZE} байт
 * </li>
 *  <li>
 * Журнал нефиксированного размера
 * </li>
 *</ol>
 * <h1>
 * Структура кластера:
 * </h1>
 * <ol>
 * <li>1 байт - число занятых в кластере байт данных</li>
 * <li>1 байт - номер следующего кластера файла или 0, если этот кластер послединй</li>
 * <li>255 байт - секция данных кластера</li>
 * </ol>
 * 
 * @author i.melentsov
 *
 */
public class FileSystem {
	/**
	 * 1 - сколько байт занято в секции данных кластера,</br>
	 * 255 - секция данных кластера,</br>
	 * 1 - номер следующего кластера файла
	 */
	private static final int BLOCK_SIZE = 257;
	/**
	 * Число кластеров в ФС
	 */
	private static final int BLOCK_NUM = 255;

	private static final int MAX_NAME_LEN = 126;
	
	private static final int FS_BLOCKS_STARTS = 256 + 1 + BLOCK_NUM * 128;
	
	private static final String DIR_FORMAT = "%63s | Размер: %4d байт\n";
	
	static final SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");

	private int allocatedBlocks = 0;

	private Map<String, Block> files = Maps.newTreeMap();
	
	private Set<Event> log = Sets.newTreeSet();
	
	/**
	 * если выставлен в true - это значит что ФС находится в состоянии отката
	 * операций и все операции проводимые ФС не должны логироваться
	 */
	private boolean undoState = false;

	private static final class Block {
		Block next;
		byte usedBytes = 0;
		byte[] data = new byte[255];
	}
	
	/**
	 * Создает новый кластер в ФС и возвращает его
	 * @return
	 */
	private Block getNewBlock(){
		if (allocatedBlocks < BLOCK_NUM) {
			allocatedBlocks++;
			return new Block();
		}
		throw new OutOfMemoryException();
	}
	
	/**
	 * Грузит ФС из файла.
	 * @param fs
	 * @throws IOException
	 */
	public void loadFileSystem(File fs) throws IOException {
		allocatedBlocks = 0;
		files.clear();
		log.clear();
		
		RandomAccessFile raf = new RandomAccessFile(fs, "r");
		byte[] unallocatedBlocks = new byte[256];
		raf.read(unallocatedBlocks);
		byte filesNum = raf.readByte();
		
		byte nameLen;
		byte[] name = new byte[MAX_NAME_LEN];
		String fileName;
		Map<String, Byte> tmpFiles = Maps.newTreeMap();
		for (int i = 0; i < getUnsignedByteValue(filesNum); ++i) {
			nameLen = raf.readByte();
			raf.read(name);
			fileName = new String(Arrays.copyOfRange(name, 0, nameLen));
			tmpFiles.put(fileName, raf.readByte());
		}
		
		Map<Byte, Block> blocks = Maps.newHashMap();
		Map<Block, Byte> blocksNext = Maps.newHashMap();
		
		int unallocatedBlockStarts;
		byte allocatedBlockStarts = 1;

		int unallocatedBlocksIndex = 0;
		Block block;
		byte next; 
		do{
			unallocatedBlockStarts = getUnsignedByteValue(unallocatedBlocks[unallocatedBlocksIndex++]);
			raf.seek(FS_BLOCKS_STARTS + (allocatedBlockStarts - 1) * BLOCK_SIZE);
			while(getUnsignedByteValue(allocatedBlockStarts) < unallocatedBlockStarts){
				block = getNewBlock();
				
				block.usedBytes = raf.readByte();
				next = raf.readByte();
				raf.read(block.data);
				
				blocks.put(allocatedBlockStarts, block);
				if(next != 0){
					blocksNext.put(block, next);
				}
				
				allocatedBlockStarts++;
				
				
			}
			allocatedBlockStarts = (byte)(unallocatedBlocks[unallocatedBlocksIndex++] + 1);
			
		}while(unallocatedBlockStarts != 255 && unallocatedBlockStarts != 0);
		
		//считать журнал
		raf.seek(FS_BLOCKS_STARTS + BLOCK_SIZE * BLOCK_NUM);
		byte[] logStr = new byte[getUnsignedByteValue(raf.readByte())];
		raf.read(logStr);
		int logSize = Integer.valueOf(new String(logStr));
		
		EventType eventType;
		int fileNameLen;
		byte[] byteFileName;
		byte[] byteDataSize;
		byte[] byteData;
		byte[] byteDate;
		byte[] byteCurIndex;
		try {
			for (int i = 0; i < logSize; ++i) {
				eventType = EventType.valueOf(raf.readByte());// тип
				fileNameLen = getUnsignedByteValue(raf.readByte());

				byteFileName = new byte[fileNameLen];// имя
				raf.read(byteFileName);

				// длина длины текста
				byteDataSize = new byte[getUnsignedByteValue(raf.readByte())];
				// длина текста
				raf.read(byteDataSize);

				byteData = new byte[Integer.valueOf(new String(byteDataSize))];
				raf.read(byteData);// текст

				byteDate = new byte[getUnsignedByteValue(raf.readByte())];
				raf.read(byteDate);// дата

				byteCurIndex = new byte[getUnsignedByteValue(raf.readByte())];
				raf.read(byteCurIndex);// индекс

				log.add(new Event(eventType, new String(byteFileName), new String(byteData),
						dateFormat.parse(new String(byteDate)), Integer.valueOf(new String(byteCurIndex))));
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		
		
		// перехреначить в связный список файлы
		for(Entry<Block, Byte> entry : blocksNext.entrySet()){
			entry.getKey().next = blocks.get(entry.getValue());
		}
		
		for(Entry<String, Byte> entry : tmpFiles.entrySet()){
			if(entry.getValue() != 0){
				files.put(entry.getKey(), blocks.get(entry.getValue()));
			}else{
				files.put(entry.getKey(), null);
			}
		}
	}
	
	/**
	 * Сохраняет образ ФС в заданый файл, дефрагментируя его.
	 * @throws IOException 
	 */
	public void createFileSystemImage(File fs) throws IOException {
		RandomAccessFile raf = new RandomAccessFile(fs, "rw");
		if (allocatedBlocks == BLOCK_NUM) {
			raf.writeByte(0);
			// raf.writeByte(0);
		} else {
			raf.writeByte(allocatedBlocks + 1);
			raf.writeByte(BLOCK_NUM);
		}
		raf.seek(256);
		// число файлов
		raf.writeByte(files.size());

		// файлы
		int i = 0;
		int lastFileEndBlock = 1;
		Block fileBlock;
		for (Entry<String, Block> entry : files.entrySet()) {
			fileBlock = entry.getValue();

			// запись имяни файла
			raf.seek(257 + i * 128);
			byte[] name = entry.getKey().getBytes();
			raf.writeByte(name.length);
			raf.write(name);

			i++;
			// запись номера первого кластера файла
			raf.seek(257 + i * 128 - 1);
			raf.writeByte(fileBlock == null ? 0 : lastFileEndBlock);

			while (fileBlock != null) {
				// записать очередной кластер файла
				raf.seek(FS_BLOCKS_STARTS + BLOCK_SIZE * (lastFileEndBlock - 1));
				raf.writeByte(fileBlock.usedBytes);
				raf.writeByte(fileBlock.next == null ? 0 : lastFileEndBlock + 1);
				raf.write(Arrays.copyOfRange(fileBlock.data, 0, getUnsignedByteValue(fileBlock.usedBytes)));

				lastFileEndBlock++;
				fileBlock = fileBlock.next;
			}
		}
		// записать журнал
		List<Byte> magazine = Lists.newArrayList();
		//длина лога сохраняется как строчка - число символов в строке (byte), сама строка
		String logSize = String.valueOf(log.size());
		magazine.add((byte)logSize.length());// число символов
		magazine.addAll(Arrays.asList(ArrayUtils.toObject(logSize.getBytes())));// размер лога
		String textSize;
		String date;
		String curIndex;
		for (Event event : log) {
			magazine.add(event.type.byteValue());// тип события
			
			magazine.add((byte)event.file.length());// длина имени
			magazine.addAll(Arrays.asList(ArrayUtils.toObject(event.file.getBytes())));// имя
			
			//число символов в строке сохраняется как строчка - число символов в строке (byte), сама строка
			textSize = String.valueOf(event.text.length());

			magazine.add((byte)textSize.length());// число символов
			magazine.addAll(Arrays.asList(ArrayUtils.toObject(textSize.getBytes())));// длина текста

			magazine.addAll(Arrays.asList(ArrayUtils.toObject(event.text.getBytes())));// текст
			
			date = dateFormat.format(event.date);
			magazine.add((byte)date.length());
			magazine.addAll(Arrays.asList(ArrayUtils.toObject(date.getBytes())));// дата
			
			
			curIndex = String.valueOf(event.curIndex);
			magazine.add((byte)curIndex.length());
			magazine.addAll(Arrays.asList(ArrayUtils.toObject(curIndex.getBytes())));// индекс
		}

		raf.seek(FS_BLOCKS_STARTS + BLOCK_SIZE * BLOCK_NUM);
		Byte[] byteLog = new Byte[magazine.size()];
		magazine.toArray(byteLog);
		raf.write(ArrayUtils.toPrimitive(byteLog));
		raf.close();
	}
	
	/**
	 * Создает новый пустой файл в ФС.
	 */
	public void createNewFile(String fileName){
		byte[] name = fileName.getBytes();
		String realName = new String(Arrays.copyOfRange(name, 0, Math.min(MAX_NAME_LEN, name.length)));
		if (files.containsKey(realName)) {
			clearFileData(realName);
		} else {
			files.put(realName, null);
			logEvent(EventType.CREATE_FILE, realName);
		}
	}
	
	public String readFile(String fileName){
		StringBuffer file = new StringBuffer();
		Block fileBlock = files.get(fileName);
		if(fileBlock == null){
			return file.toString();
		}
		while(fileBlock.next != null){
			file.append(new String(fileBlock.data));
			fileBlock = fileBlock.next;
		}
		file.append(new String(Arrays.copyOfRange(fileBlock.data, 0, getUnsignedByteValue(fileBlock.usedBytes))));
		return file.toString();
	}
	
	public void writeToFile(String fileName, String t){
		createNewFile(fileName);
		appendToFile(fileName, t);
	}
	
	public void appendToFile(String fileName, String t){
		if(t.length() == 0)
			return;
		if(!files.containsKey(fileName)){
			createNewFile(fileName);
		}
		Block fileBlock = files.get(fileName);
		if(fileBlock == null){
			fileBlock = getNewBlock();
			files.put(fileName, fileBlock);
		}
		while(fileBlock.next != null){
			fileBlock = fileBlock.next;
		}
		int start = getUnsignedByteValue(fileBlock.usedBytes);
		int end = 255;
		byte[] text = t.getBytes();
		int textLength = text.length;
		int textBegin = 0;
		int write;
		while(textLength > end - start){
			write = end - start;
			System.arraycopy(text, textBegin, fileBlock.data, start, write);
			textLength -= write;
			textBegin += write;
			start = 0;
			fileBlock.usedBytes = (byte) 255;
			fileBlock.next = getNewBlock();
			fileBlock = fileBlock.next;
		}
		System.arraycopy(text, textBegin, fileBlock.data, start, textLength);
		fileBlock.usedBytes += (byte) textLength;
		logEvent(EventType.APPEND_TO_FILE, fileName, t);
	}
	
	private void clearFileData(String fileName){
		String fileData = readFile(fileName);
		Block fileBlock = files.get(fileName);
		while(fileBlock != null){
			allocatedBlocks--;
			fileBlock = fileBlock.next;
		}
		files.put(fileName, fileBlock);
		if(!fileData.equals("")){
			logEvent(EventType.CLEAR_FILE, fileName, fileData);
		}
	}
	
	/**
	 * Удаляет файл и освобождает выделенные под него кластеры
	 * @param fileName
	 */
	public void deleteFile(String fileName) {
		if (files.containsKey(fileName)) {
			clearFileData(fileName);
			files.remove(fileName);
			logEvent(EventType.DELETE_FILE, fileName);
		}
	}
	
	/**
	 * Возвращает реальный размер файла в байтах
	 * @param fileName
	 * @return
	 */
	public int getFileSize(String fileName){
		int size = 0;
		Block fileBlock = files.get(fileName);
		while(fileBlock != null){
			size += getUnsignedByteValue(fileBlock.usedBytes);
			fileBlock = fileBlock.next;
		}
		return size;
	}
	
	/**
	 * Выводит на stdout имена всех файлов в ФС с их размерами 
	 */
	public void dir() {
		for(String fileName : files.keySet()){
			System.out.format(DIR_FORMAT, fileName, getFileSize(fileName));
		}
	}
	
	/**
	 * Выводит на stdout журнал ФС
	 */
	public void showLog(){
		int i = 0;
		for(Event event : log){
			System.out.println(String.format("%3d. %s", ++i,  event));
			
		}
	}
	
	/**
	 * Откатывает count последних событий из журнала
	 * @param count
	 */
	public void undo(int count){
		 undoEvent(0, count);
	 }
	 
	
	/**
	 * Откатывает count событий c from-той позиции журнала
	 * @param count
	 */
	private void undoEvent(int from, int count){
		if(count > log.size() || count < 0){
			throw new IllegalArgumentException();
		}
		if(count == 0){
			return;
		}
		Event event;
		Event[] events = new Event[log.size()];
		log.toArray(events);
		
		boolean tempUndoState = undoState;
		undoState = true;
		int i = 0;
		while(i < count){
			event = events[i + from];
			
			//undo event
			switch (event.type) {
			case CREATE_FILE:
				deleteFile(event.file);
				break;
			case APPEND_TO_FILE:
				String fileData = readFile(event.file);
				writeToFile(event.file, fileData.substring(0, fileData.length() - event.text.length()));
				break;
			case DELETE_FILE:
				createNewFile(event.file);
				break;
			case CLEAR_FILE:
				appendToFile(event.file, event.text);
				break;
			case UNDO:
				int undoCount = Integer.valueOf(event.text);
				for (int j = undoCount; j > 0; --j) {
					doEvent(i + from + j);
				}
			}

			++i;
		}
		undoState = tempUndoState;
		logEvent(EventType.UNDO, "", String.valueOf(count));
	}
	
	/**
	 * Обработка события отмены события отмены
	 * undoState - при вызове всегда установлен
	 * @param i - индекс события которое должно быть выполнено
	 */
	private void doEvent(int i){
		Event[] events = new Event[log.size()];
		log.toArray(events);
		Event event = events[i];
		
		//do event
		switch (event.type) {
		case CREATE_FILE:
			createNewFile(event.file);
			break;
		case APPEND_TO_FILE:
			appendToFile(event.file, event.text);
			break;
		case DELETE_FILE:
			deleteFile(event.file);
			break;
		case CLEAR_FILE:
			clearFileData(event.file);
			break;
		case UNDO:
			undoEvent(i + 1, Integer.valueOf(event.text));
		}
		
		
	}
	
	
	private static final String TOUCH = "touch";
	private static final String SAVE = "save";
	private static final String LOAD = "load";
	private static final String SHOW = "show";
	private static final String LOG = "log";
	private static final String UNDO = "undo";
	private static final String CAT = "cat";
	private static final String EXIT = "exit";
	private static final String DIR = "dir";
	private static final String INVITATION = "$ ";
	private static final String ARG_SEPARATOR = " ";
	private static final String REMOVE = "rm";
	private static final String REMOVE_FLAG = "-f";
	
	private static final char REROUTING = '>';
	
	private static final String UNDO_EXC = "Откат невозможен.\n Число отменяемых действий либо больше общего числа действий в журнале либо меньше нуля.";	
	
	/**
	 * @param args
	 * @throws IOException 
	 */
	public static void main(String[] args) throws IOException {
		FileSystem fs = new FileSystem();
		if(args.length > 0){
			File file = new File(args[0]);
			if(file.exists() && file.isFile()){
				try {
					fs.loadFileSystem(file);
				} catch (IOException e) {
					e.printStackTrace();
					System.exit(0);
				}
			}
		}
		Scanner reader = new Scanner(System.in);
		String line;
		String[] parts;
		while (true) {
			System.out.print(INVITATION);
			line = reader.nextLine();
			if (line.equals(EXIT)) {
				System.exit(0);
			} else if (line.equals(DIR)) {
				fs.dir();
				continue;
			}
			parts = line.split(ARG_SEPARATOR);
			if (parts[0].equals(CAT)) {
				if (parts[1].charAt(0) == REROUTING) {
					if (parts[1].charAt(1) == REROUTING) {
						fs.appendToFile(parts[1].substring(2), reader.nextLine());
					} else {
						fs.writeToFile(parts[1].substring(1), reader.nextLine());
					}
				} else {
					System.out.println(fs.readFile(parts[1]));
				}
			} else if (parts[0].equals(REMOVE)) {
				if (parts[1].equals(REMOVE_FLAG)) {
					fs.deleteFile(parts[2]);
				}
			} else if (parts[0].equals(TOUCH)) {
				fs.createNewFile(parts[1]);
			} else if (parts[0].equals(SAVE)) {
				fs.createFileSystemImage(new File(parts[1]));
			} else if (parts[0].equals(LOAD)) {
				fs.loadFileSystem(new File(parts[1]));
			} else if(parts[0].equals(SHOW)){
				if (parts[1].equals(LOG)) {
					fs.showLog();
				}
			}else if(parts[0].equals(UNDO)){
				try{
					fs.undo(Integer.valueOf(parts[1]));
				}catch(Exception e){
					System.out.println(UNDO_EXC);
				}
				
			}
		}
	}
	

	private void logEvent(EventType type, String file){
		logEvent(type, file, "");
	}
	
	private void logEvent(EventType type, String file, String text){
		if(undoState){
			return;
		}
		Event event = new Event(type, file, text, Calendar.getInstance().getTime());
		log.add(event);
	}
	
	private static int getUnsignedByteValue(byte b) {
		return b & 0xFF;
	}
	
	@SuppressWarnings("serial")
	private class OutOfMemoryException extends RuntimeException {
		private static final String MESS = "Out of memory.";
		
		public OutOfMemoryException(){
			super(MESS);
		}
	}

}
