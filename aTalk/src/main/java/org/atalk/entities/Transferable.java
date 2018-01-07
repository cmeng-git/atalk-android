package org.atalk.entities;

import java.util.*;

public interface Transferable
{

	List<String> VALID_IMAGE_EXTENSIONS = Arrays.asList("webp", "jpeg", "jpg", "png", "jpe");
	List<String> VALID_CRYPTO_EXTENSIONS = Arrays.asList("pgp", "gpg", "otr");
	List<String> WELL_KNOWN_EXTENSIONS = Arrays.asList("pdf","m4a","mp4","3gp","aac","amr","mp3");

	int STATUS_UNKNOWN = 0x200;
	int STATUS_CHECKING = 0x201;
	int STATUS_FAILED = 0x202;
	int STATUS_OFFER = 0x203;
	int STATUS_DOWNLOADING = 0x204;
	int STATUS_DELETED = 0x205;
	int STATUS_OFFER_CHECK_FILESIZE = 0x206;
	int STATUS_UPLOADING = 0x207;


	boolean start();

	int getStatus();

	long getFileSize();

	int getProgress();

	void cancel();
}
