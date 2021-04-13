package eoepca;

import lombok.extern.log4j.Log4j2;

import static eoepca.Convert.toStr;

@Log4j2
public class Tracer {

	@FunctionalInterface
	public interface ThrowingConsumer {

		default void accept(boolean throwing, String name, Object... objects) {
			try {
				log.info("{} called: {}", name, toStr(objects));
				Long startTime = System.nanoTime();
				acceptThrows();
				Long endTime = System.nanoTime();
				log.info("{} succeeded after {}ms", name, (endTime - startTime) / 1000000);
			} catch (final Exception ex) {
				log.error("{} failed: {}", name, ex instanceof ClientException ? ex.getMessage() : ex);
				if (throwing)
					throw new RuntimeException(ex);
			}
		}

		void acceptThrows() throws Exception;
	}

	@FunctionalInterface
	public interface ThrowingFunction<R> {

		default R apply(boolean throwing, String name, Object... objects) {
			try {
				log.info("{} called: {}", name, toStr(objects));
				Long startTime = System.nanoTime();
				R r = applyThrows();
				Long endTime = System.nanoTime();
				log.info("{} succeeded after {}ms", name, (endTime - startTime) / 1000000);
				return r;
			} catch (final Exception ex) {
				log.error("{} failed: {}", name, ex instanceof ClientException ? ex.getMessage() : ex);
				if (throwing)
					throw new RuntimeException(ex);
				return null;
			}
		}

		R applyThrows() throws Exception;
	}

	public static void Throwing(String name, ThrowingConsumer consumer) {
		consumer.accept(true, name);
	}

	public static <P1> void Throwing(String name, P1 p1, ThrowingConsumer consumer) {
		consumer.accept(true, name, p1);
	}

	public static <P1, P2> void Throwing(String name, P1 p1, P2 p2, ThrowingConsumer consumer) {
		consumer.accept(true, name, p1, p2);
	}

	public static <P1, P2, P3> void Throwing(String name, P1 p1, P2 p2, P3 p3, ThrowingConsumer consumer) {
		consumer.accept(true, name, p1, p2, p3);
	}

	public static <P1, P2, P3, P4> void Throwing(String name, P1 p1, P2 p2, P3 p3, P4 p4, ThrowingConsumer consumer) {
		consumer.accept(true, name, p1, p2, p3, p4);
	}

	public static <P1, P2, P3, P4, P5> void Throwing(String name, P1 p1, P2 p2, P3 p3, P4 p4, P5 p5, ThrowingConsumer consumer) {
		consumer.accept(true, name, p1, p2, p3, p4, p5);
	}

	public static <R> R Throwing(String name, ThrowingFunction function) {
		return (R) function.apply(true, name);
	}

	public static <R, P1> R Throwing(String name, P1 p1, ThrowingFunction function) {
		return (R) function.apply(true, name, p1);
	}

	public static <R, P1, P2> R Throwing(String name, P1 p1, P2 p2, ThrowingFunction function) {
		return (R) function.apply(true, name, p1, p2);
	}

	public static <R, P1, P2, P3> R Throwing(String name, P1 p1, P2 p2, P3 p3, ThrowingFunction function) {
		return (R) function.apply(true, name, p1, p2, p3);
	}

	public static <R, P1, P2, P3, P4> R Throwing(String name, P1 p1, P2 p2, P3 p3, P4 p4, ThrowingFunction function) {
		return (R) function.apply(true, name, p1, p2, p3, p4);
	}

	public static <R, P1, P2, P3, P4, P5> R Throwing(String name, P1 p1, P2 p2, P3 p3, P4 p4, P5 p5, ThrowingFunction function) {
		return (R) function.apply(true, name, p1, p2, p3, p4, p5);
	}

	public static void Catching(String name, ThrowingConsumer consumer) {
		consumer.accept(false, name);
	}

	public static <P1> void Catching(String name, P1 p1, ThrowingConsumer consumer) {
		consumer.accept(false, name, p1);
	}

	public static <P1, P2> void Catching(String name, P1 p1, P2 p2, ThrowingConsumer consumer) {
		consumer.accept(false, name, p1, p2);
	}

	public static <P1, P2, P3> void Catching(String name, P1 p1, P2 p2, P3 p3, ThrowingConsumer consumer) {
		consumer.accept(false, name, p1, p2, p3);
	}

	public static <P1, P2, P3, P4> void Catching(String name, P1 p1, P2 p2, P3 p3, P4 p4, ThrowingConsumer consumer) {
		consumer.accept(false, name, p1, p2, p3, p4);
	}

	public static <P1, P2, P3, P4, P5> void Catching(String name, P1 p1, P2 p2, P3 p3, P4 p4, P5 p5, ThrowingConsumer consumer) {
		consumer.accept(false, name, p1, p2, p3, p4, p5);
	}

	public static <R> R Catching(String name, ThrowingFunction function) {
		return (R) function.apply(false, name);
	}

	public static <R, P1> R Catching(String name, P1 p1, ThrowingFunction function) {
		return (R) function.apply(false, name, p1);
	}

	public static <R, P1, P2> R Catching(String name, P1 p1, P2 p2, ThrowingFunction function) {
		return (R) function.apply(false, name, p1, p2);
	}

	public static <R, P1, P2, P3> R Catching(String name, P1 p1, P2 p2, P3 p3, ThrowingFunction function) {
		return (R) function.apply(false, name, p1, p2, p3);
	}

	public static <R, P1, P2, P3, P4> R Catching(String name, P1 p1, P2 p2, P3 p3, P4 p4, ThrowingFunction function) {
		return (R) function.apply(false, name, p1, p2, p3, p4);
	}

	public static <R, P1, P2, P3, P4, P5> R Catching(String name, P1 p1, P2 p2, P3 p3, P4 p4, P5 p5, ThrowingFunction function) {
		return (R) function.apply(false, name, p1, p2, p3, p4, p5);
	}
}
