import org.junit.Test;



public class PageTest {
	
	private static final int ITERATIONS = 33554432;
	
	@Test
	public void pageTest(){
		System.out.println("\n\n	PageTest:");
		int maxMemory = (int) Runtime.getRuntime().freeMemory() - 500; // сколько памяти доступно jvm, чуть уменьшил что бы не нарваться на gc, вдруг  Math.random(), где-то память выделяет
		byte[] memory = new byte[maxMemory];
		long startTime, endTime;
		int honesty;
		
		startTime = System.currentTimeMillis();
		for(int i = 0; i < ITERATIONS; ++i){
			honesty = i%maxMemory;// что бы избежать оптимизации неиспользуемой переменной
			memory[honesty] = (byte) i;
			honesty = (int) (Math.random() * maxMemory);// для чистоты эксперимента
		}
		endTime = System.currentTimeMillis();
		long result1 = endTime - startTime;
		System.out.println(String.format("Successively %d ms", result1));
		
		startTime = System.currentTimeMillis();
		for(int i = 0; i < ITERATIONS; ++i){
			honesty = (int) (Math.random() * maxMemory);
			memory[honesty] = (byte) i;
			honesty = i%maxMemory;
		}
		endTime = System.currentTimeMillis();
		long result2 = endTime - startTime;
		System.out.println(String.format("Unsuccessively %d ms", result2));
		System.out.println(String.format("Result: %d < %d", result1, result2));
	}
}
