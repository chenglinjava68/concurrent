package dbcache.test;



public class ProxyEntity extends Entity {

	private Entity obj;

	@Override
	public void setNum(int num) {
		Object oldValue = Integer.valueOf(this.obj.getNum());
		this.obj.setNum(num);
		Object newValue = Integer.valueOf(this.obj.getNum());
	}



}