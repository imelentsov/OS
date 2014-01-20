import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.Map;
import java.util.Map.Entry;

import com.google.common.collect.Maps;

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
	
	private int allocatedBlocks = 0;

	private Map<String, Block> files = Maps.newTreeMap();

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
		// кластера
		raf.seek(FS_BLOCKS_STARTS + BLOCK_SIZE * BLOCK_NUM - 1);
		raf.writeByte(0);
		raf.close();
	}
	
	/**
	 * Создает новый пустой файл в ФС.
	 */
	public void createNewFile(String fileName){
		byte[] name = fileName.getBytes();
		String realName = new String(Arrays.copyOfRange(name, 0, Math.min(MAX_NAME_LEN, name.length)));
		if(files.containsKey(realName)){
			deleteFile(realName);
		}
		files.put(realName, null);
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
	}
	
	/**
	 * Удаляет файл и освобождает выделенные под него кластеры
	 * @param fileName
	 */
	public void deleteFile(String fileName){
		Block fileBlock = files.get(fileName);
		while(fileBlock != null){
			allocatedBlocks--;
			fileBlock = fileBlock.next;
		}
		files.remove(fileName);
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
			System.out.format("%63s | Размер: %4d байт\n", fileName, getFileSize(fileName));
		}
	}
	
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
		BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
		String line;
		String[] parts;
		while(true){
			if(reader.ready()){
				line = reader.readLine();
				if(line.equals("exit")){
					System.exit(0);
				}else if(line.equals("dir")){
					fs.dir();
					continue;
				}
				parts = line.split(" ");
				if(parts[0].equals("cut")){
					if(parts[1].charAt(0) == '>'){
						if(parts[1].charAt(1) == '>'){
							fs.appendToFile(parts[1].substring(2), reader.readLine() + "\n");
						}else{
							fs.writeToFile(parts[1].substring(1), reader.readLine() + "\n");
						}
					}else{
						System.out.println(fs.readFile(parts[1]));
					}
				}else if(parts[0].equals("rm")){
					if(parts[1].equals("-f")){
						fs.deleteFile(parts[2]);
					}
				}else if(parts[0].equals("touch")){
					fs.createNewFile(parts[1]);
				}else if(parts[0].equals("save")){
					fs.createFileSystemImage(new File(parts[1]));
				}else if(parts[0].equals("load")){
					fs.loadFileSystem(new File(parts[1]));
				}
			}
		}
	}

	private static int getUnsignedByteValue(byte b) {
		return b & 0xFF;
	}
	
	@SuppressWarnings("serial")
	private class OutOfMemoryException extends RuntimeException {
		public OutOfMemoryException(){
			super("Out of memory.");
		}
	}

}
