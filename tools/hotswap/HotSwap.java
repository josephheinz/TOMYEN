import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Properties;
import java.util.stream.Stream;

import com.sun.jdi.Bootstrap;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.connect.AttachingConnector;
import com.sun.jdi.connect.Connector;

/**
 * Pushes recompiled classes into a game started with {@code ./gradlew runClientHotswap}.
 *
 * <p>Args: {@code [--baseline] <port> <hash file> <class dir>...}. {@code --baseline} records the class
 * hashes the game is about to load. Otherwise only classes whose contents differ from the last push
 * (or the baseline) are sent; Gradle rewrites unchanged class files, so timestamps aren't enough.
 * Classes the game hasn't loaded yet are skipped since they'll load fresh from disk.
 */
public final class HotSwap {
	public static void main(String[] args) throws Exception {
		boolean baseline = args[0].equals("--baseline");
		int first = baseline ? 1 : 0;
		String port = args[first];
		Path hashFile = Path.of(args[first + 1]);
		List<Path> classDirs = Stream.of(args).skip(first + 2).map(Path::of).filter(Files::isDirectory).toList();

		Properties hashes = new Properties();
		Map<String, Path> files = new HashMap<>();
		for (Path dir : classDirs) {
			try (Stream<Path> walk = Files.walk(dir)) {
				for (Path file : walk.filter(f -> f.toString().endsWith(".class")).toList()) {
					String name = dir.relativize(file).toString().replace(".class", "").replace('/', '.');
					hashes.setProperty(name, sha256(file));
					files.put(name, file);
				}
			}
		}

		if (baseline) {
			save(hashFile, hashes);
			return;
		}

		Properties previous = new Properties();
		if (Files.exists(hashFile)) {
			try (InputStream in = Files.newInputStream(hashFile)) {
				previous.load(in);
			}
		}
		List<String> changed = new ArrayList<>();
		for (String name : hashes.stringPropertyNames()) {
			if (!hashes.getProperty(name).equals(previous.getProperty(name))) {
				changed.add(name);
			}
		}

		if (changed.isEmpty()) {
			System.out.println("hotswap: no changed classes");
			return;
		}

		VirtualMachine vm;
		try {
			vm = attach(port);
		} catch (ConnectException e) {
			System.err.println("hotswap: no game listening on port " + port + ". Start it with ./gradlew runClientHotswap");
			System.exit(1);
			return;
		}

		try {
			Map<ReferenceType, byte[]> redefinitions = new HashMap<>();
			for (String name : changed) {
				for (ReferenceType type : vm.classesByName(name)) {
					redefinitions.put(type, Files.readAllBytes(files.get(name)));
				}
			}

			if (redefinitions.isEmpty()) {
				System.out.println("hotswap: " + changed.size() + " changed class(es), none loaded yet; nothing to push");
			} else {
				vm.redefineClasses(redefinitions);
				for (ReferenceType type : redefinitions.keySet()) {
					boolean mixin = type.name().contains(".mixin.");
					System.out.println("hotswap: reloaded " + type.name()
							+ (mixin ? " (mixin: re-transforms its target class; restart if the game misbehaves)" : ""));
				}
			}
		} catch (UnsupportedOperationException e) {
			System.err.println("hotswap: the JVM rejected this change (" + e.getMessage() + "). Restart the game.");
			System.exit(1);
		} finally {
			vm.dispose();
		}

		save(hashFile, hashes);
	}

	private static String sha256(Path file) throws IOException, NoSuchAlgorithmException {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
	}

	private static void save(Path hashFile, Properties hashes) throws IOException {
		Files.createDirectories(hashFile.getParent());
		try (OutputStream out = Files.newOutputStream(hashFile)) {
			hashes.store(out, null);
		}
	}

	private static VirtualMachine attach(String port) throws IOException {
		AttachingConnector connector = Bootstrap.virtualMachineManager().attachingConnectors().stream()
				.filter(c -> c.name().equals("com.sun.jdi.SocketAttach"))
				.findFirst()
				.orElseThrow();
		Map<String, Connector.Argument> arguments = connector.defaultArguments();
		arguments.get("hostname").setValue("127.0.0.1");
		arguments.get("port").setValue(port);
		try {
			return connector.attach(arguments);
		} catch (com.sun.jdi.connect.IllegalConnectorArgumentsException e) {
			throw new IllegalStateException(e);
		}
	}
}
