package demo.cryptocurrency;

import lombok.SneakyThrows;
import utils.CryptographicUtils;

import java.io.File;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;

public class KeyLoader {

	@SneakyThrows
	public static List<PublicKey> readKeysFromFiles(String repoPathname) {
		List<String> pathnames = readPathnames(repoPathname);
		return pathnames.stream().map(CryptographicUtils::readECDSAPublicKey).collect(Collectors.toList());
	}

	@SneakyThrows
	private static List<String> readPathnames(String repoPathname) {
		File repoFile = new File(repoPathname);
		List<String> pathnames = new ArrayList<>();
		try (Scanner reader = new Scanner(repoFile)) {
			while (reader.hasNextLine()) {
				pathnames.add(reader.nextLine().trim());
			}
		}
		return pathnames;
	}

}
