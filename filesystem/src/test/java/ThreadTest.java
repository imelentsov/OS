import org.apache.commons.lang3.mutable.MutableBoolean;
import org.junit.Test;

public class ThreadTest {
	private static final String[] fileNames = {
			"Тест1",
			"Тест2",
			"0123456789012345678901234567890123456789012345678901234567890123456789",  //имя   < 126 байт.
			"яяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяя" };// имя > 126 байт.
	private static final String[] fileContents = {
			"Привет, как дела?",
			"Нормально.",
			"яяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяя0123456789012345678901234Привет!!!" };

	/**
	 * Блок synchronized - используется только для получения монитора ресурса и дальнейшей остановки/возобновления исполнения треда
	 */
	@Test
	public void synchronousAccess() {
		final FileSystem fs = new FileSystem();
		for (int i = 0; i < fileNames.length; i++) {
			fs.createNewFile(fileNames[i]);
			if(i<fileContents.length){
				fs.writeToFile(fileNames[i], fileContents[i]);
			}
		}
		// специально выставлен запрос ресурса у второго треда и его очередь,
		// что бы заблокировать первый тред
		final boolean[] flags = {false, true};
		final MutableBoolean turn = new MutableBoolean(true);

		Thread th1 = new Thread() {
			public void run() {
				flags[0] = true;
				while (flags[1] == true) {
					if (turn.isTrue()) {
						flags[0] = false;
						while (turn.isTrue()) {
							synchronized(fs){
								try {
									System.out.println("In Sync");
									fs.wait();
									System.out.println("Wacked up");
								} catch (InterruptedException e) {
									e.printStackTrace();
								}
							}
						}
						flags[0] = true;
					}
				}
				
				fs.dir();
				System.out.println();
				
				turn.setValue(true);
				flags[0] = false;
				synchronized (fs) {
					fs.notify();
				}
			}
		};
		
		Thread th2 = new Thread() {
			public void run() {
				flags[1] = true;
				while (flags[0] == true) {
					if (turn.isFalse()) {
						flags[0] = false;
						while (turn.isFalse()) {
							synchronized(fs){
								try {
									fs.wait();
								} catch (InterruptedException e) {
									e.printStackTrace();
								}
							}
						}
						flags[1] = true;
					}
				}
				
				fs.dir();
				System.out.println();
				
				turn.setValue(false);
				flags[1] = false;
				synchronized (fs) {
					fs.notify();
				}
			}
		};
		
		th1.start();
		th2.start();
	}
}
