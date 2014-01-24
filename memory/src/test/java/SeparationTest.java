import java.lang.reflect.Constructor;

import org.junit.Test;

public class SeparationTest {
	private static final int ITERATIONS = 1000000;
	private static final int PROCESS_NUM = 15;

	private static class FastThread extends Thread {
		private volatile static int[][] counters = new int[PROCESS_NUM][256];
		private int countIndex;

		FastThread(int countIndex) {
			this.countIndex = countIndex;
		}

		public void run() {
			for (int i = 0; i < ITERATIONS; ++i) {
				counters[countIndex][0]++;
			}
		}
	}

	private static class SlowThread extends Thread {
		private volatile static int[] counters = new int[PROCESS_NUM];
		private int countIndex;

		SlowThread(int countIndex) {
			this.countIndex = countIndex;
		}

		public void run() {
			for (int i = 0; i < ITERATIONS; ++i) {
				counters[countIndex]++;
			}
		}
	}

	long profile(Class<? extends Thread> action) throws Exception {
		Thread[] threads = new Thread[PROCESS_NUM];
		Constructor<? extends Thread> con = action.getDeclaredConstructor(Integer.TYPE);
		long startTime = System.currentTimeMillis();
		for (int i = 0; i < PROCESS_NUM; ++i) {
			threads[i] = con.newInstance(i);
			threads[i].start();
		}
		for (int i = 0; i < PROCESS_NUM; ++i) {
			threads[i].join();
		}
		return System.currentTimeMillis() - startTime;
	}

	@Test
	public void separate() throws Exception {
		System.out.println("\n\n	SeparationTest:");
		long first = profile(FastThread.class);
		long second = profile(SlowThread.class);
		System.out.println(String.format("With separation: %d ms", first));
		System.out.println(String.format("With out separation: %d ms", second));
		System.out.println(String.format("Result: %d < %d", first, second));
		System.out.println();
	}
}
