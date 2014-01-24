import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;

import org.junit.Test;

import com.google.common.collect.Lists;

public class ThreadTest {

	private static final int ITERATIONS = 50000;
	private static final int PROCESS_NUM = 30;
	private static final int[] BLOCK_SIZES = { 256, 512, 768, 1024 };

	private static void action() {
		List<byte[]> allocatedMemory = Lists.newArrayList();
		int blockSize;
		int index;
		byte[] block;
		for (int i = 0; i < ITERATIONS; ++i) {
			index = (int) (Math.random() * allocatedMemory.size());
			switch ((int) (Math.random() * 3)) {
			case 0:
				blockSize = BLOCK_SIZES[(int) (Math.random() * BLOCK_SIZES.length)];
				allocatedMemory.add(new byte[blockSize]);
				break;
			case 1:
				if (allocatedMemory.size() != 0) {
					allocatedMemory.remove(index);
				}
				break;
			case 2:
				if (allocatedMemory.size() != 0) {
					block = allocatedMemory.get(index);
					for (int j = 0; j < block.length; ++j) {
						block[j] = 0;
					}
				}
			}
		}

	}

	@Test
	public void profile() throws InterruptedException {
		System.out.println("\n\n	ThreadTest:");
		List<Callable<Void>> threads = Lists.newArrayList();
		long threadsStartTime = System.currentTimeMillis();

		ExecutorService threadPool = Executors.newFixedThreadPool(PROCESS_NUM);
		
		// may be reused by pool
		Callable<Void> thread = new Callable<Void>() {
			@Override
			public Void call() throws Exception {
				action();
				return null;
			}
		};
		
		for (int i = 0; i < PROCESS_NUM; ++i) {
			threads.add(thread);
		}
		threadPool.invokeAll(threads);
		long threadsEndTime = System.currentTimeMillis();

		System.out.println("Threads");
		System.out.println(String.format("Total time: %d ms", threadsEndTime - threadsStartTime));
		System.out.println();

		ForkJoinPool forkPool = new ForkJoinPool(PROCESS_NUM);
		long forkStartTime = System.currentTimeMillis();
		for (int i = 0; i < PROCESS_NUM; ++i) {
			forkPool.invoke(new RecursiveAction() {// may be invoked by pool only once -> create new instance 
				@Override
				protected void compute() {
					action();
				}
			});
		}
		long forkEndTime = System.currentTimeMillis();

		System.out.println("Forks");
		System.out.println(String.format("Total time: %d ms", forkEndTime - forkStartTime));

		System.out.println(String.format("Result: %d < %d", forkEndTime - forkStartTime, threadsEndTime - threadsStartTime));
	}
}
