import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;

public class Main {
	public static void main(String[] args) throws InterruptedException,
			IOException {
		int numThreads = Integer.parseInt (args[1]);;
		FPSPList list = new FPSPList();
		Test[] test = new Test[numThreads];
		BufferedReader br = null;
		BufferedWriter bw = null;
		long startTime, endTime, numOperation;
		int[] locations = new int[1000000];
		int [][] dir=new int[100000][5];
		for (int i = 0; i < locations.length; i++) {
			locations[i] = 100 + 8 * i;
			

		}
		locations = randomizeArray(locations);
		for (int i = 0; i < locations.length; i++) {
			// System.out.println("Thread " + tid + " : " + locations[i]);
		}

		try {
			br = new BufferedReader(new FileReader(args[0]));
			//bw = new BufferedWriter(new FileWriter(args[1]));
		} catch (IOException e) {
			e.printStackTrace();
		}

		startTime = System.currentTimeMillis();

		Integer dirCount=0;
		Integer locCount=0;
		for (int i = 0; i < numThreads; i++) {
			
			test[i] = new Test(i, list, br,locations,dir);
			test[i].start();
		}

		try {
			for (int i = 0; i < numThreads; i++)
				test[i].t.join();
		} catch (Exception e) {
			System.out.println("Interrupted");
		}
		System.out.println("\n\nDIRECTORY");
		System.out.println("-----------");
		System.out.println("FileID"+"\t"+"Start"+"\t"+"End");
		System.out.println("-----------------------");
		for (int i = 0; dir[i][1]!=0; i++) 
			System.out.println(dir[i][0]+"\t"+dir[i][1]+"\t"+dir[i][3]);
		endTime = System.currentTimeMillis();

		numOperation = 1000000 / (endTime - startTime);

		numOperation = numOperation * 2000;

		BufferedWriter bww = null;

		bww = new BufferedWriter(new FileWriter("Graph.txt", true));

		bww.write(String.valueOf(numOperation) + " ");

		bww.close();

		ArrayList<Integer> items = new ArrayList<Integer>();

		items = list.visit();

		for (Integer i : items) {
			bw.write(i + "\n");
			bw.flush();
		}
	}

	public static int[] randomizeArray(int[] array) {
		Random rgen = new Random(); // Random number generator

		for (int i = 0; i < array.length; i++) {
			int randomPosition = rgen.nextInt(array.length);
			int temp = array[i];
			array[i] = array[randomPosition];
			array[randomPosition] = temp;
		}

		return array;
	}
}
