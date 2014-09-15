JFLAGS = -g
JCC = javac
JVM = java


default: Main.class Approximation.class FPSPList.class Node.class Test.class VersionedAtomicMarkableReference.class Window.class


Main.class: Main.java
	$(JCC) $(JFLAGS) Main.java



classes: $(CLASSES:.java=.class)

clean:
	$(RM) *.class
