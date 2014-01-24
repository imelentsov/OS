import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.Test;

import com.google.common.collect.Lists;

public class MatrixTest {
	private static final int DIMENSION = 1024;
	private static final int PROCESS_NUM = 4;

	public static abstract class AbstractMatrix<T extends Element> {
		protected int dimension;

		public AbstractMatrix(int dimension) {
			this.dimension = dimension;
		}

		public abstract T getCell(int i, int j);

		public abstract void setCell(int i, int j, T element);

		public long multiply(AbstractMatrix<T> rightOper,
				AbstractMatrix<T> result) throws MatrixException {
			if (dimension != rightOper.dimension) {
				throw new MatrixException();
			}
			long startTime = System.currentTimeMillis();

			List<Callable<Void>> threads = Lists.newArrayList();
			ExecutorService threadPool = Executors.newFixedThreadPool(PROCESS_NUM);

			for (int i = 0; i < dimension; ++i) {
				threads.add(new LineProcessor(rightOper, result, i));
			}
			try {
				threadPool.invokeAll(threads);
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}

			return System.currentTimeMillis() - startTime;
		}

		private class LineProcessor implements Callable<Void> {
			AbstractMatrix<T> rightOper;
			AbstractMatrix<T> result;
			int resultLine;

			public LineProcessor(AbstractMatrix<T> rightOper,
					AbstractMatrix<T> result, int resultLine) {
				this.rightOper = rightOper;
				this.result = result;
				this.resultLine = resultLine;
			}

			@Override
			public Void call() throws Exception {
				for (int i = 0; i < AbstractMatrix.this.dimension; ++i) {
					result.setCell(resultLine, i,
							(T) result.getCell(resultLine, i).zero());
					for (int j = 0; j < AbstractMatrix.this.dimension; ++j) {
						result.setCell(resultLine, i, (T) result.getCell(resultLine, i)
								.add(AbstractMatrix.this.getCell(resultLine,j).multiply(rightOper.getCell(j, i))));
					}
				}
				return null;
			}

		}

		private static class MatrixException extends Exception {
			private static final String MESSAGE = "Dimensions are different";

			private MatrixException() {
				super(MESSAGE);
			}
		}
	}

	public static abstract class Element {
		public abstract Element zero();

		public abstract Element add(Element operand);

		public abstract Element multiply(Element operand);
	}
	
	public class Matrix<T extends  Element> extends AbstractMatrix<T>{
		Element[][] matrix;
		
		public Matrix(int dimension) {
			super(dimension);
			matrix = new Element[dimension][dimension];
		}

		@Override
		public T getCell(int i, int j) {
			return (T) matrix[i][j];
		}

		@Override
		public void setCell(int i, int j, T element) {
			matrix[i][j] = element;
		}
		
	}
	
	public class BlockedMatrix<T extends  Element> extends AbstractMatrix<T>{
		Element[][][][] matrix;
		int blockSizeMinusOne;
		int blockSizeLog2 ;
		
		/**
		 * 
		 * @param dimension - степень 2 и sqrt(dimension) - есть целое число
		 */
		public BlockedMatrix(int dimension) {
			super(dimension);
			blockSizeLog2 = 0;
			while ((dimension & 1) == 0) {
				dimension >>= 1;
				++blockSizeLog2;
			}
			blockSizeLog2 >>= 1;
			int blockSize = (1 << blockSizeLog2); 
			matrix = new Element[blockSize][blockSize][blockSize][blockSize];
			blockSizeMinusOne = blockSize - 1;
		}

		@Override
		public T getCell(int i, int j) {
			return (T) matrix[i >> blockSizeLog2][j >> blockSizeLog2][i & blockSizeMinusOne][j & blockSizeMinusOne];
		}

		@Override
		public void setCell(int i, int j, T element) {
			matrix[i >> blockSizeLog2][j >> blockSizeLog2][i & blockSizeMinusOne][j & blockSizeMinusOne] = element;
		}
		
	}

	public static class IntElement extends Element {
		int element;

		IntElement(int element) {
			this.element = element;
		}

		@Override
		public Element zero() {
			return new IntElement(0);
		}

		@Override
		public Element add(Element operand) {
			return new IntElement(element + ((IntElement) operand).element);
		}

		@Override
		public Element multiply(Element operand) {
			return new IntElement(element * ((IntElement) operand).element);
		}
		
		public int intValue() {
			return element;
		}
	}
	
	private void fillMatrix(AbstractMatrix<IntElement> matrix){
		for(int i = 0; i< matrix.dimension; ++i ){
			for(int j = 0; j< matrix.dimension; ++j){
				matrix.setCell(i, j, new IntElement(i * matrix.dimension + j));
			}
		}
	}
	
	@Test
	public void profile() throws MatrixTest.AbstractMatrix.MatrixException {
		System.out.println("\n\n	MatrixTest:");
		AbstractMatrix<IntElement> matrix = new BlockedMatrix<IntElement>(DIMENSION);
		AbstractMatrix<IntElement> result = new Matrix<IntElement>(DIMENSION);
		fillMatrix(matrix);
		fillMatrix(result);
		System.out.println("Starting blocked multiplying..");
		long res1 = matrix.multiply(matrix, result);
		System.out.println("Finished.");
//		result:
		
//		for(int i = 0; i< result.dimension; ++i ){
//			for(int j = 0; j< result.dimension; ++j){;
//			System.out.print(result.getCell(i, j).intValue() + ", ");
//			}
//			System.out.println();
//		}
//		
//		System.out.println();
		
		
		matrix = new Matrix<IntElement>(DIMENSION);
		fillMatrix(matrix);
		System.out.println("Starting usual multiplying..");
		long res2 = matrix.multiply(matrix, result);
		System.out.println("Finished.");
		System.out.println();
		System.out.println(String.format("Normal matrix %d ms",res2));
		System.out.println(String.format("Blocked matrix %d ms",res1));
		System.out.println(String.format("Result: %d < %d",res1, res2));
	}
}