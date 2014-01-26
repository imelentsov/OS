/**
 * Тип события
 * @author i.melentsov
 */
enum EventType {
	CREATE_FILE(0, "Был создан"), APPEND_TO_FILE(1, "Был дописан: %s"),
	DELETE_FILE(2, "Был удален"), CLEAR_FILE(3, "Содержимое было удалено: %s"),
	UNDO(4, "Откат последних %s действий");
	
	byte value;
	String strValue;
	
	EventType(int value, String strValue) {
		this.value = (byte) value;
		this.strValue = strValue;
	}

	public byte byteValue() {
		return value;
	}

	public static EventType valueOf(byte b) {
		switch (b) {
		case 0:
			return CREATE_FILE;
		case 1:
			return APPEND_TO_FILE;
		case 2:
			return DELETE_FILE;
		case 3:
			return CLEAR_FILE;
		case 4:
			return UNDO;
		default:
			return null;
		}
	}

}