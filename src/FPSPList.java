import java.util.ArrayList;
import java.util.concurrent.atomic.*;

public class FPSPList
{
	enum OpType { insert, search_delete, execute_delete, success, failure, determine_delete, contains, update_approximation };

	private final int numThreads = 5;
	private final Node head, tail;
	private final AtomicReferenceArray<OpDesc> state;
	private final AtomicLong currentMaxPhase;
	private final HelpRecord helpRecords[];
	private final long HELPING_DELAY = 3;
	private final int MAX_FAILURES = 5;
	private final int width = 128;
	private AtomicReference<Approximation> app;
	private final int[] difCouners;
	private final int soft_threshold = 35;
	private final int hard_threshold = 50;

	private class OpDesc
	{
		public final long phase;
		public final OpType type;
		public final Node node;
		public final Window searchResult;

		public OpDesc (long ph, OpType ty, Node n, Window sResult)
		{
			phase = ph;
			type = ty;
			node = n;
			searchResult = sResult;
		}
	}
	
	class HelpRecord
	{
		int curTid;
		long lastPhase;
		long nextCheck;
		
		public HelpRecord ()
		{
			curTid = -1;
			reset ();
		}
		
		public void reset ()
		{
			curTid = (curTid + 1) % numThreads;
			lastPhase = state.get (curTid).phase;
			nextCheck = HELPING_DELAY;
		}
	}
		
	public FPSPList ()
	{
		currentMaxPhase = new AtomicLong ();
		currentMaxPhase.set (1);
		
		head = new Node (Integer.MIN_VALUE);
		tail = new Node (Integer.MAX_VALUE);
		
		head.next = new VersionedAtomicMarkableReference<Node> (tail, false);
		tail.next = new VersionedAtomicMarkableReference<Node> (tail, false);
		
		state = new AtomicReferenceArray<OpDesc> (numThreads);
		helpRecords = new HelpRecord[numThreads * width];
		
		for (int i = 0; i < state.length (); i++)
		{
			state.set (i, new OpDesc (0, OpType.success, null, null));
			helpRecords[i * width] = new HelpRecord ();
		}
		
		difCouners = new int[numThreads * width];
		app = new AtomicReference<Approximation> (new Approximation (0, -1, -1));
	}
	
	private void helpIfNeeded (int tid)
	{
		HelpRecord rec = helpRecords[tid * width];
		
		if (rec.nextCheck-- == 0)
		{
			OpDesc desc = state.get (rec.curTid);
			
			if (desc.phase == rec.lastPhase)
			{
				if (desc.type == OpType.insert)
					helpInsert (rec.curTid, rec.lastPhase);
				else if (desc.type == OpType.search_delete || desc.type == OpType.execute_delete)
					helpDelete (rec.curTid, rec.lastPhase);
				else if (desc.type == OpType.contains)
					helpContains (rec.curTid, rec.lastPhase);
				else if (desc.type == OpType.update_approximation)
					helpUpdateGlobalCounter (rec.curTid, rec.lastPhase);
			}
			
			rec.reset ();
		}
	}
	
	public boolean insert (int tid, int key, int loc)
	{
		if (updateGlobalCounterIfNeeded (tid, difCouners[tid * width]))
			difCouners[tid * width] = 0;
		
		helpIfNeeded (tid);
		
		int tries = 0;
		
		while (tries++ < MAX_FAILURES)
		{
			Window window = fastSearch (key, tid);
		
			if (window == null)
			{
				boolean result = slowInsert (tid, key);
			
				if (result)
					difCouners[tid * width]++;
				
				return result;
			}
			
			Node pred = window.pred, curr = window.curr;
			
			if (curr.key == key)
				return false;
			else
			{
				Node node = new Node (key,loc);
				node.next = new VersionedAtomicMarkableReference<Node> (curr, false);
			
				if (pred.next.compareAndSet (curr, node, false, false))
					return true;
			}
		}
		
		boolean result = slowInsert (tid, key);
		
		if (result)
			difCouners[tid * width]++;
		
		return result;
	}
	
	public boolean delete (int tid, int key)
	{
		if (updateGlobalCounterIfNeeded (tid, difCouners[tid * width]))
			difCouners[tid * width] = 0;
		
		helpIfNeeded (tid);
	
		int tries = 0;
		boolean snip;
		
		while (tries++ < MAX_FAILURES)
		{
			Window window = fastSearch (key, tid);
			
			if (window == null)
			{
				boolean result = slowDelete (tid, key);
			
				if (result)
					difCouners[tid * width]--;
				
				return result;
			}
			
			Node pred = window.pred, curr = window.curr;
			
			if (curr.key != key)
				return false;
			else
			{
				Node succ = curr.next.getReference ();
				snip = curr.next.compareAndSet (succ, succ, false, true);
			
				if (!snip)
					continue;
				
				pred.next.compareAndSet (curr, succ, false, false);
				boolean result = curr.successBit.compareAndSet (false, true);
				
				if (result)
					difCouners[tid * width]--;
				
				return result;
			}
		}
		
		boolean result = slowDelete (tid, key);
		
		if (result)
			difCouners[tid * width]--;
		
		return result;
	}
	
	final long MaxError = numThreads * hard_threshold;
	
	public Window fastSearch (int key, int tid)
	{
		long maxSteps = sizeApproximation () + MaxError;
		int tries = 0;
		Node pred = null, curr = null, succ = null;
		boolean[] marked = {false};
		boolean snip;

		retry : while (tries++ < MAX_FAILURES)
		{
			long steps = 0;
			pred = head;
			curr = pred.next.getReference ();
		
			while (true)
			{
				steps++;
				if (steps >= maxSteps)
				{
					return null;
				}
				succ = curr.next.get (marked);
			
				while (marked[0])
				{
					if (steps >= maxSteps)
					{
						return null;
					}
					
					snip = pred.next.compareAndSet (curr, succ, false, false);
				
					if (!snip)
						continue retry;
					
					curr = succ;
					succ = curr.next.get (marked);
					steps++;
				}

				if (curr.key >= key)
					return new Window (pred, curr);
				
				pred = curr;
				curr = succ;
			}
		}
		
		return null;
	}
	
	private boolean slowInsert (int tid, int key)
	{
		long phase = maxPhase ();
		Node n = new Node (key);

		n.next = new VersionedAtomicMarkableReference<Node> (null, false);
		OpDesc op = new OpDesc (phase, OpType.insert, n, null);
		
		state.set (tid, op);

		helpInsert (tid, phase);
		
		return state.get (tid).type == OpType.success;
	}
	
	private boolean slowDelete (int tid, int key)
	{
		long phase = maxPhase ();
		state.set (tid, new OpDesc (phase, OpType.search_delete, new Node (key), null));

		helpDelete (tid, phase);
		OpDesc op = state.get (tid);
	
		if (op.type == OpType.determine_delete)	
			return op.searchResult.curr.successBit.compareAndSet(false, true);
		
		return false;
	}
	
	private Window search (int key, int tid, long phase)
	{
		Node pred = null, curr = null, succ = null;
		boolean[] marked = {false};
		boolean snip;
		
		retry : while (true)
		{
			pred = head;
			curr = pred.next.getReference ();
		
			while (true)
			{
				succ = curr.next.get (marked);
				
				while (marked[0])
				{	
					snip = pred.next.compareAndSet(curr, succ, false, false);
				
					if (!isSearchStillPending (tid, phase))
						return null;
					
					if (!snip)
						continue retry;
					
					curr = succ;
					succ = curr.next.get (marked);
				}
				
				if (curr.key >= key)
					return new Window (pred, curr);
				
				pred = curr;
				curr = succ;
			}
		}
	}
	
	private void helpInsert (int tid, long phase)
	{
		while (true)
		{
			OpDesc op = state.get (tid);
		
			if (!(op.type == OpType.insert && op.phase == phase))
				return;
			
			Node node = op.node;
			Node node_next = node.next.getReference ();
			
			Window window = search (node.key, tid, phase);
			
			if (window == null)
				return;
			
			if (window.curr.key == node.key)
			{
				if ((window.curr == node) || (node.next.isMarked()))
				{	
					OpDesc success = new OpDesc (phase, OpType.success, node, null);
				
					if (state.compareAndSet(tid, op, success))
						return;
				}
				else
				{
					OpDesc fail = new OpDesc (phase, OpType.failure, node, null);
					
					if (state.compareAndSet(tid, op, fail))
						return;
				}
			}
			else
			{
				if (node.next.isMarked())
				{
					OpDesc success = new OpDesc (phase, OpType.success, node, null);
				
					if (state.compareAndSet (tid, op, success))
						return;
				}
				
				int version = window.pred.next.getVersion ();
				OpDesc newOp = new OpDesc (phase, OpType.insert, node, null);
				
				if (!state.compareAndSet (tid, op, newOp))
					continue;
				
				node.next.compareAndSet (node_next, window.curr, false, false);
				
				if (window.pred.next.compareAndSet (version, node_next, node, false, false))
				{
					OpDesc success = new OpDesc (phase, OpType.success, node, null);
				
					if (state.compareAndSet (tid, newOp, success))
						return;
				}
			}
		}
	}
	
	private void helpDelete (int tid, long phase)
	{
		while (true)
		{
			OpDesc op = state.get (tid);
			
			if (!((op.type == OpType.search_delete || op.type == OpType.execute_delete) && op.phase == phase))
				return;

			Node node = op.node;
			
			if (op.type == OpType.search_delete)
			{
				Window window = search (node.key, tid, phase);
				
				if (window == null)
					continue;
				
				if (window.curr.key != node.key)
				{	
					OpDesc failure = new OpDesc (phase, OpType.failure, node, null);
				
					if (state.compareAndSet (tid, op, failure))
						return;
				}
				else
				{	
					OpDesc found = new OpDesc (phase, OpType.execute_delete, node, window);
					state.compareAndSet(tid, op, found);
				}
			}
			else if (op.type == OpType.execute_delete)
			{
				Node next = op.searchResult.curr.next.getReference ();

				if (!op.searchResult.curr.next.attemptMark (next, true))
					continue;
				
				search (op.node.key, tid, phase);
				OpDesc determine = new OpDesc (op.phase, OpType.determine_delete, op.node, op.searchResult);
				
				state.compareAndSet (tid, op, determine);
				
				return;
			}
		}
	}
	
	public boolean contains (int tid, int key)
	{
		long maxSteps = sizeApproximation () + MaxError;
		long steps = 0;
		boolean[] marked = {false};
		Node curr = head;
	
		while (curr.key < key)
		{
			curr = curr.next.getReference ();
			curr.next.get (marked);
	
			if (steps++ >= maxSteps)
				return slowContains (tid, key);
		}
		
		return (curr.key == key && !marked[0]);
	}
	
	private boolean slowContains (int tid, int key)
	{
		long phase = maxPhase ();
		Node n = new Node (key);
		OpDesc op = new OpDesc (phase, OpType.contains, n, null);

		state.set (tid, op);
		
		helpContains (tid, phase);
		
		return state.get (tid).type == OpType.success;
	}
	
	private void helpContains (int tid, long phase)
	{
		OpDesc op = state.get (tid);
	
		if (!((op.type == OpType.contains) && op.phase == phase))
			return;
		
		Node node = op.node;
		Window window = search (node.key, tid, phase);
		
		if (window == null)
			return;
		
		if (window.curr.key == node.key)
		{
			OpDesc success = new OpDesc (phase, OpType.success, node, null);
			state.compareAndSet (tid, op, success);
		}
		else
		{
			OpDesc failure = new OpDesc (phase, OpType.failure, node, null);
			state.compareAndSet (tid, op, failure);
		}
	}
	
	private long maxPhase ()
	{
		long result = currentMaxPhase.get ();
		
		currentMaxPhase.compareAndSet(result, result+1);
	
		return result;
	}
	
	private boolean isSearchStillPending (int tid, long ph)
	{
		OpDesc curr = state.get (tid);
	
		return ((curr.type == OpType.insert || curr.type == OpType.search_delete || curr.type == OpType.execute_delete || curr.type == OpType.contains) && curr.phase == ph);
	}
	
	private boolean updateGlobalCounterIfNeeded (int tid, int updateSize)
	{
		if (Math.abs (updateSize) < soft_threshold)
			return false;
	
		Approximation old = app.get ();
		
		if (old.tid == -1)
		{
			Approximation newApp = new Approximation (old.app_size + updateSize, -1, -1);
		
			if (app.compareAndSet(old, newApp))
				return true;
		}
		
		if (Math.abs (updateSize) < hard_threshold)
			return false;
		
		long phase = maxPhase ();
		Node n = new Node (updateSize);
		OpDesc desc = new OpDesc (phase, OpType.update_approximation, n, null);
		
		state.set (tid, desc);
		helpUpdateGlobalCounter (tid, phase);
		
		return true;
	}
	
	private void helpUpdateGlobalCounter (int tid, long phase)
	{
		while (true)
		{
			OpDesc op = state.get (tid);
		
			if (!((op.type == OpType.update_approximation) && op.phase == phase))
				return;
			
			Approximation oldApp = app.get ();
			
			if (op != state.get (tid))
				return;
			
			if (oldApp.tid != -1)
			{
				OpDesc helpedTid = state.get (oldApp.tid);
			
				if (helpedTid.phase == oldApp.phase && helpedTid.type == OpType.update_approximation) 
				{	
					OpDesc success = new OpDesc (helpedTid.phase, OpType.success, helpedTid.node, null);
					state.compareAndSet (oldApp.tid, helpedTid, success);
				}
				
				Approximation clean = new Approximation (oldApp.app_size, -1, -1);
				app.compareAndSet(oldApp, clean);
				
				continue;
			}
			
			int updateSize = op.node.key;
			Approximation newApp = new Approximation (oldApp.app_size + updateSize, tid, phase);
			app.compareAndSet (oldApp, newApp);
		}
	}
	
	private long sizeApproximation ()
	{
		return app.get ().app_size;
	}
	
	public ArrayList<Integer> visit()
	{
		Node r = head.next.getReference();
		Node t = tail.next.getReference();
		
		ArrayList<Integer> item = new ArrayList<Integer>();

		while(!r.equals(t))
		{
			item.add (r.key);
			r = r.next.getReference();
		}
		
		return item;
	}
}