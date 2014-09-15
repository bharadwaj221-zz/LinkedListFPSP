import java.util.concurrent.atomic.AtomicReference;

public class VersionedAtomicMarkableReference<V>
{
	private static class ReferenceBooleanTriplet<T>
	{
		private final T reference;
		private final boolean bit;
		private final int version;

		ReferenceBooleanTriplet (T r, boolean i, int v)
		{
			reference = r;
			bit = i;
			version = v;
		}
	}

	private final AtomicReference<ReferenceBooleanTriplet<V>> atomicRef;

	public VersionedAtomicMarkableReference (V initialRef, boolean initialMark)
	{
		atomicRef = new AtomicReference<ReferenceBooleanTriplet<V>> (new ReferenceBooleanTriplet<V>(initialRef, initialMark, 0));
	}

	public V getReference ()
	{
		return atomicRef.get().reference;
	}

	public boolean isMarked ()
	{
		return atomicRef.get().bit;
	}

	public V get (boolean[] markHolder)
	{
		ReferenceBooleanTriplet<V> p = atomicRef.get();
		markHolder[0] = p.bit;
		return p.reference;
	}

	public boolean compareAndSet (V expRef, V newRef, boolean expMark, boolean newMark)
	{
		ReferenceBooleanTriplet<V> curr = atomicRef.get();
		
		return expRef == curr.reference && expMark == curr.bit && ((newRef == curr.reference && newMark == curr.bit) || atomicRef.compareAndSet(curr, new ReferenceBooleanTriplet<V>(newRef, newMark, curr.version + 1)));
	}

	public void set (V newRef, boolean newMark)
	{
		ReferenceBooleanTriplet<V> curr = atomicRef.get();
		
		if (newRef != curr.reference || newMark != curr.bit)
			atomicRef.set (new ReferenceBooleanTriplet<V>(newRef, newMark, curr.version + 1));
	}
	
	public boolean attemptMark (V expectedReference, boolean newMark)
	{
		ReferenceBooleanTriplet<V> curr = atomicRef.get();
		
		return expectedReference == curr.reference && (newMark == curr.bit || atomicRef.compareAndSet(curr, new ReferenceBooleanTriplet<V>(expectedReference, newMark, curr.version+1))); 
	}

	public int getVersion ()
	{
		return atomicRef.get().version;
	}
	
	public boolean compareAndSet (int ver, V expRef, V newRef, boolean expMark, boolean newMark)
	{
		ReferenceBooleanTriplet<V> curr = atomicRef.get();
		
		return expRef == curr.reference && expMark == curr.bit && ver == curr.version && ((newRef == curr.reference && newMark == curr.bit) || atomicRef.compareAndSet (curr, new ReferenceBooleanTriplet<V> (newRef, newMark, curr.version + 1)));
	}
}