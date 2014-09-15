import java.util.concurrent.atomic.AtomicBoolean;

public class Node
{
	public final int key;
	public VersionedAtomicMarkableReference<Node> next;
	public final AtomicBoolean successBit;
	private int loc;

	public Node (int key)
	{
		this.key = key;
		successBit = new AtomicBoolean (false);
	}
	public Node (int key, int loc)
	{
		this.key = key;
		this.loc=loc;
		successBit = new AtomicBoolean (false);
	}
}