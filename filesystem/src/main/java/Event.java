import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;
/**
 * Событие в ФС
 * @author i.melentsov
 *
 */
class Event implements Comparable<Event>{
	static AtomicInteger index = new AtomicInteger(0);
		
	static final String LOG_FORMAT = "%s | Файл: %63s | %s";
	static final String LOG_UNDO_FORMAT = "%s |%71s| %s";
	
	String file;
	Date date;
	EventType type;
	String text;
	int curIndex;

	public Event(EventType type, String file, String text, Date date){
		this.type = type;
		this.file = file;
		this.text = text;
		this.date = date;
		curIndex = index.addAndGet(1);
	}
	
	public Event(EventType type, String file, String text, Date date, int index){
		this.type = type;
		this.file = file;
		this.text = text;
		this.date = date;
		curIndex = index;
		if(Event.index.get() < index){
			Event.index.set(index);
		}
	}

	public Date getDate() {
		return date;
	}

	public void setDate(Date date) {
		this.date = date;
	}

	public EventType getType() {
		return type;
	}

	public void setType(EventType type) {
		this.type = type;
	}

	public String getText() {
		return text;
	}

	public void setText(String text) {
		this.text = text;
	}

	public int compareTo(Event arg0) {
		return arg0.curIndex - curIndex;
	}
	
	public String toString(){
		if(type == EventType.UNDO){
			return String.format(LOG_UNDO_FORMAT,  FileSystem.dateFormat.format(date), file, String.format(type.strValue, text));
		}
		return String.format(LOG_FORMAT, FileSystem.dateFormat.format(date), file, String.format(type.strValue, text)) ;
	}
}