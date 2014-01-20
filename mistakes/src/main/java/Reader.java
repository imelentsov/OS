import java.io.BufferedReader;
import java.io.EOFException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.nio.channels.FileLockInterruptionException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import com.google.common.collect.TreeMultiset;

public class Reader {
	private TreeMultiset<DeliriumInteger> numbers =  TreeMultiset.create();
	
	private static final String NOT_DIR_PATTERN = "Директория \'%s\' не существует.";
	private static final String ACCESS_DIR_PATTERN = "Доступ к директории \'%s\' запрещен.";
	private static final String BAD_FILE = "Невозможно открыть файл или прочитать из него.";
	private static final String NO_MEM = "Память кончилась.";
	private static final String EXC_PATTERN = "Ошибка: \'%s\'\nСтек трейс:\n \"%s\"";
	private static final String VIRTUAL_EXC_PATTERN = "Ошибка виртуальной машины: \'%s\'";
	
	public static void main(String[] args) {
		Reader reader = new Reader();
		try {
			reader.findSafe(args[0]);
		} catch (OutOfMemoryError e) {
			log(e, NO_MEM);
		} finally {
			for (DeliriumInteger num : reader.numbers) {
				System.out.println(num);
			}
			System.exit(0);
		}
	}

	/**
	 * Идет по всем файлам и папкам в заданной директоории, находит и сортирует числа
	 * находящиеся в них.
	 * @param directory
	 * @return
	 */
	public void findSafe(String directory)  {
		Path path = Paths.get(directory);
		if(!path.toFile().isDirectory()) {
			log(String.format(NOT_DIR_PATTERN, directory));
			return;
		}
		try {
			Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path file,
						BasicFileAttributes attrs) {
					try {
						processFile(file.toFile());
					} catch (Exception e) {
						log(e, BAD_FILE);
					}
					return FileVisitResult.CONTINUE;
				}
			});
		} catch (IOException e) {
			log(String.format(ACCESS_DIR_PATTERN, directory));
		}
	}

	/**
	 * обрабатывает очередную строчку файла
	 * @return
	 */
	private void processLine(String line) {
		int i = 0; 
		while(i<line.length() && Character.isDigit(line.charAt(i))){
			i++;
		}
		if(i != 0){
			numbers.add(new DeliriumInteger(Integer.valueOf(line.substring(0, i))));
		}
	}

	/**
	 * обрабатывает очередной файл
	 * @throws IOException - если файл не открылся или не удалось получить очередную строчку
	 */
	private void processFile(File file) throws IOException {
		BufferedReader curFileReader = new DeliriumReader(new FileReader(file));
		String line;
		while((line = curFileReader.readLine()) != null){
			processLine(line);
		}
	}
	
	private static void log(Exception e, String message){
		log(message);
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		e.printStackTrace(pw);
		System.out.println(String.format(EXC_PATTERN, e.getMessage(),sw.toString()));
	}
	
	private static void log(VirtualMachineError e, String message){
		log(message);
		System.out.println(String.format(VIRTUAL_EXC_PATTERN, e.getMessage()));
	}
	
	private static void log(String message){
		System.out.println(message);
	}
	
	public static boolean rave(double probability){
		return  Math.random() <= probability;
	}
	
	public static void rave(double probability, RuntimeException[] excseptions){
		if(rave(probability)){
			throw excseptions[(int) ((Math.random() * excseptions.length))];
		}
	}
	
	private static class DeliriumReader extends BufferedReader  {
		private static final RuntimeException[] excseptions = new RuntimeException[]{   
					new RuntimeException(new EOFException()),  
					new RuntimeException(new FileLockInterruptionException()),
					new RuntimeException(new UnsupportedEncodingException())
					};
		private static final double PROBABILITY = 0.075;
		
		public DeliriumReader(java.io.Reader reader){
			super(reader);
			if(rave(PROBABILITY)){
				throw new RuntimeException(new FileNotFoundException());
			}
		}
		
		public String readLine() throws IOException{
			rave(PROBABILITY,excseptions);
			return super.readLine();
		}
	}
	
	private static class DeliriumInteger  implements java.lang.Comparable<DeliriumInteger>{
		private static final double PROBABILITY = 0.125;
		private static final String NO_MEM = "PermGen space";
		private Integer a;
		
		public DeliriumInteger(Integer a){
			if(rave(PROBABILITY)){
				throw new OutOfMemoryError(NO_MEM);
			}
			this.a = a;
		}

		public boolean equals(Object arg0) {
			return this == arg0;
		}
		
		public int hashCode() {
			return a.hashCode();
		}
		
		public String toString() {
			return a.toString();
		}

		public int compareTo(DeliriumInteger arg0) {
			return a.compareTo(arg0.a);
		}
		
	}

}
