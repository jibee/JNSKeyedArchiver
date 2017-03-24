package com.jibee.nsKeyedArchive;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dd.plist.NSArray;
import com.dd.plist.NSDictionary;
import com.dd.plist.NSNumber;
import com.dd.plist.NSObject;
import com.dd.plist.NSString;
import com.dd.plist.UID;

public class NSKA {
	private static final Logger logger = LoggerFactory.getLogger(NSKA.class);
	private NSArray data;
	private NSDictionary rootObject;
	
	private NSObject getValueAt(NSObject id)
	{
		UID root = (UID)id;
		byte b = root.getBytes()[0];
		
		return data.objectAtIndex(b);
		
	}
	
	private NSKA(NSDictionary archive) {
		NSDictionary top = (NSDictionary) archive.get("$top");
		String archiver = archive.get("$archiver").toString();
		data = (NSArray)archive.get("$objects");
		rootObject = (NSDictionary) getValueAt(top.get("root"));
	}
	
	public <T> T as(Class<T> clazz) throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException
	{
		return as(rootObject, clazz);
	}
	
	private <T> T as(NSDictionary spec, Class<T> clazz) throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException
	{
		T data = clazz.getConstructor().newInstance();
		return into(spec, data);
	}
	
	public <T> T into(T target) throws NoSuchMethodException, SecurityException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		return into(rootObject, target);
	}

	public <T> T into(NSDictionary spec, T target) throws NoSuchMethodException, SecurityException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		Class clazz = target.getClass(); 
		
		String className = getClassName(spec);


		Map<String, Object> items=asMap(spec);
		
		for(Entry<String, Object> field: items.entrySet())
		{
			String name = field.getKey();
			name = name.replaceFirst(name.substring(0,1), name.substring(0,1).toUpperCase());
			Object o = field.getValue();
			Class fieldType = o.getClass();
			String setterName = "set"+name;
			try{
				Method setter = clazz.getMethod(setterName, fieldType);
				setter.invoke(target, o);
			}
			catch(NoSuchMethodException e)
			{
				boolean found = false;
				for(Method m: clazz.getMethods())
				{
					if(setterName.equals(m.getName()) && 1==m.getParameterTypes().length)
					{
						if(fieldType.isInstance(o))
						{
							m.invoke(target, o);
							found = true;
							break;
						}
						try {
							Object sub = as((NSDictionary)field.getValue(), m.getParameterTypes()[0]);
							m.invoke(target, sub);
							found = true;
							break;
						} catch (InstantiationException e1) {
							// TODO Auto-generated catch block
							e1.printStackTrace();
						}
					}
				}
				if(!found)
					throw e;
			}
		}
		return target;
	}

	private Map<String, Object> asMap(NSDictionary spec) {
		NSArray keys = (NSArray) spec.get("NS.keys");
		NSArray values = (NSArray) spec.get("NS.objects");
		assert keys.count() == values.count();
		

		Map<String, Object> items = new HashMap<>();
		for(int i = 0; i<keys.count(); ++i)
		{
			NSString key = (NSString)getValueAt(keys.objectAtIndex(i));
			Object value = objectForNS(getValueAt(values.objectAtIndex(i)));
			items.put(key.toString(), value);
			logger.debug("{} = ({})={}", key.toString(), value.getClass().getName(), value.toString());
		}
		return items;
	}

	private String getClassName(NSDictionary spec) {
		NSDictionary classHierarchy = (NSDictionary) getValueAt(spec.get("$class"));
		NSArray classes = (NSArray)classHierarchy.get("$classes");
		return classes.objectAtIndex(0).toString();
	}

	private Object objectForNS(NSObject value) {
		switch(value.getClass().getSimpleName())
		{
		case "NSArray":
			return Collection.class;
		case "NSData":
			return null;
		case "NSDate":
			return Date.class;
		case "NSDictionary":
		{
			NSDictionary d = ((NSDictionary)value);
			if(d.containsKey("$class"))
			{
				// Sub-object most likely
				String className = getClassName(d);
				if("NSMutableDictionary".equals(className))
				{
					return asMap(d);
				}
			}
			return d;
		}
		case "NSNumber":
		{
			NSNumber nval = ((NSNumber)value);
			if(nval.type()==NSNumber.BOOLEAN)
			{
				return Boolean.valueOf(nval.boolValue());
			}
			if(nval.type()==NSNumber.INTEGER)
			{
				return Integer.valueOf(nval.intValue());
			}
			if(nval.type()==NSNumber.REAL)
			{
				return Double.valueOf(nval.doubleValue());
			}
		}
		case "NSSet":
			return Set.class;
		case "NSString":
			return ((NSString)value).toString();
		case "UID":
			return UID.class;
		default:
			return Object.class;
		}
	}

	private Class classForNS(NSObject value) {
		switch(value.getClass().getSimpleName())
		{
		case "NSArray":
			return Collection.class;
		case "NSData":
			return null;
		case "NSDate":
			return Date.class;
		case "NSDictionary":
			return Map.class;
		case "NSNumber":
			switch(((NSNumber)value).type())
			{
			case NSNumber.BOOLEAN: return Boolean.class;
			case NSNumber.REAL: return Double.class;
			case NSNumber.INTEGER: return Integer.class;
			}
			return Double.class;
		case "NSSet":
			return Set.class;
		case "NSString":
			return String.class;
		case "UID":
			return UID.class;
		default:
			return Object.class;
		}
	}

	public static NSKA deserialize(NSDictionary archive)
	{
		NSKA retval = new NSKA(archive);
		return retval;
	}
	
	static <T> T deserialize(NSObject archive, Class<T> clazz)
	{
		return null;
	}

}
