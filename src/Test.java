import java.io.BufferedReader;
import java.io.IOException;
import java.util.Random;

public class Test extends Thread {
	int tid;
	Thread t;
	FPSPList list;
	BufferedReader br;
	int[] locations;
	static Integer counter;
	private int[][] dir;
	static Integer dirCounter;

	public Test(int tid, FPSPList list, BufferedReader br, int[] locations2,
			int[][] dir) {
		this.tid = tid;
		this.list = list;
		this.br = br;
		this.dir = dir;
		this.locations = locations2;
		counter = 0;
		dirCounter = 0;

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

	public void run() {
		String temp;
		String[] words;
		int size = 0, chunks = 0;
		try {
			while ((temp = br.readLine()) != null) {

				words = temp.split(" ");

				if (words[0].equalsIgnoreCase("STORE")) {
					// counter++;

					size = Integer.parseInt(words[1]);
					chunks = (int) Math.ceil(size / 8.0);

					
						
						synchronized (locations) {
							counter++;
							int start = locations[counter];
							Integer startCount = counter;
							for (int i = 0; i < chunks; i++) {

								if (counter >= locations.length) {
									System.err.println("Memory full");
									System.exit(0);
								} else {
									list.insert(tid, counter,
											locations[counter]);
									System.out.println("Thread: " + tid
											+ " storing file " + words[2]
											+ " chunk " + (i + 1) + " at "
											+ locations[counter]);
									counter++;
								}
							}
								int end = locations[counter];
								Integer endCount = counter;
								try
								{
								synchronized (dir) {
									
									dir[dirCounter][0] = Integer
											.parseInt(words[2]);
									dir[dirCounter][1] = start;
									dir[dirCounter][2] = startCount;
									dir[dirCounter][3] = end;
									dir[dirCounter][4] = endCount;
									synchronized (dirCounter) {
										
									
									dirCounter++;
									}

								}
								}
								catch(ArrayIndexOutOfBoundsException e)
								{
									System.out.println("Directory structure full.");
								}

							}
						
					
				} else if (words[0].equalsIgnoreCase("FREE")) {
					int fileid = Integer.parseInt(words[1]);
					boolean flag = false;
					System.out.println(dirCounter);
					for (int i = 0; i < dirCounter; i++)
						if (dir[i][0] == fileid) {
							flag = true;
							int startCount = dir[i][2];
							int endCount = dir[i][4];
							System.out.println("SC=" + startCount + "  EC="
									+ endCount);
							dir[i][0] = -1;
							for (int j = startCount; j <= endCount; j++)
								list.delete(tid, Integer.parseInt(words[1]));
						}
					if (!flag) {
						System.out.println("File ID " + fileid + " not found");
					}
				}
				// if (list.insert (tid, Integer.parseInt (words[1]))) {}
				// else if (words[0].equals("D"))
				// if (list.delete (tid, Integer.parseInt (words[1]))) {}
				// else
				// if (list.contains (tid, Integer.parseInt (words[1]))) {}
			}
		} catch (NumberFormatException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void start() {
		t = new Thread(this, String.valueOf(tid));
		t.start();
	}
}