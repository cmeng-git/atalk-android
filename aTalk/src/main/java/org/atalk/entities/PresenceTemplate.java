package org.atalk.entities;

import android.content.ContentValues;
import android.database.Cursor;

import net.java.sip.communicator.service.protocol.PresenceStatus;
import net.java.sip.communicator.service.protocol.globalstatus.GlobalStatusEnum;


public class PresenceTemplate extends AbstractEntity {

	public static final String TABLE_NAME = "presence_templates";
	public static final String LAST_SEEN = "last_seen";
	public static final String MESSAGE = "message";
	public static final String STATUS = "status";

	private long lastSeen = 0;
	private String statusMessage;
	private PresenceStatus status = GlobalStatusEnum.ONLINE;

	public PresenceTemplate(PresenceStatus status, String statusMessage) {
		this.status = status;
		this.statusMessage = statusMessage;
		this.lastSeen = System.currentTimeMillis();
		this.uuid = java.util.UUID.randomUUID().toString();
	}

	private PresenceTemplate() {

	}

	@Override
	public ContentValues getContentValues() {
		final String show = status.toString();
		ContentValues values = new ContentValues();
		values.put(LAST_SEEN, lastSeen);
		values.put(MESSAGE, statusMessage);
		values.put(STATUS, show == null ? "" : show);
		values.put(UUID, uuid);
		return values;
	}

	public static PresenceTemplate fromCursor(Cursor cursor) {
		PresenceTemplate template = new PresenceTemplate();
		template.uuid = cursor.getString(cursor.getColumnIndex(UUID));
		template.lastSeen = cursor.getLong(cursor.getColumnIndex(LAST_SEEN));
		template.statusMessage = cursor.getString(cursor.getColumnIndex(MESSAGE));
		template.status = GlobalStatusEnum.getStatusByName(cursor.getString(cursor.getColumnIndex
				(STATUS)));
		return template;
	}

	public PresenceStatus getStatus() {
		return status;
	}

	public String getStatusMessage() {
		return statusMessage;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		PresenceTemplate template = (PresenceTemplate) o;

		if (statusMessage != null
				? !statusMessage.equals(template.statusMessage)
				: template.statusMessage != null)
			return false;
		return status == template.status;

	}

	@Override
	public int hashCode() {
		int result = statusMessage != null ? statusMessage.hashCode() : 0;
		result = 31 * result + status.hashCode();
		return result;
	}
}
