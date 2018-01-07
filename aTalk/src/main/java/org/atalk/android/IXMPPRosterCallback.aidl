package org.atalk.android;

/*
	IPC interface for XMPPService to send broadcasts to UI
*/

interface IXMPPRosterCallback {
	void connectionStatusChanged(boolean isConnected, boolean willReconnect);
	void onAuthorizationFailed(String reason);
	void onFailureDueToMultiLogin(String reason);
	void onChatRoomCreated(String roomName, String subject, int messageType);
}
