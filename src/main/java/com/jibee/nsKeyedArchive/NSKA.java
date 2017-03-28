/*
* This file is a Java-based reader for the NSKeyedArchive object serialisation
* protocol.
*
* Copyright (C) 2017 Jean-Baptiste Mayer <jean-baptiste.mayer@m4x.org>
* 
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
* 
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
* 
* You should have received a copy of the GNU General Public License
* along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

package com.jibee.nsKeyedArchive;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dd.plist.NSArray;
import com.dd.plist.NSData;
import com.dd.plist.NSDate;
import com.dd.plist.NSDictionary;
import com.dd.plist.NSNumber;
import com.dd.plist.NSObject;
import com.dd.plist.NSSet;
import com.dd.plist.NSString;
import com.dd.plist.UID;

/**
 * NSKeyedArchive object deserialisation.
 * 
 * This class provides object deserialisation from objects serialized with the
 * NSKeyedArchive protocol.
 * 
 * This is (apparently) a failry common way to serialise object in the Apple
 * world - where the object is serialised into a NSDictionnary itself
 * serialised into a PList.
 * 
 * To use, do one of the following:
 * 
 * My o=new My();
 * NSKA.deserialise(dict).into(o);
 * 
 * or
 * 
 * My o = NSKA.deserialise(dict).as(My.class);
 * 
 * or
 * 
 * Map<String, Object> map = NSKA.deserialise(dict).asMap();
 * 
 * @author jibee
 *
 */
public class NSKA {
	/** Prepare a deserialiser for the given archive dictionary
	 * 
	 * @param archive
	 * @return
	 */
	public static NSKA deserialize(NSDictionary archive)
	{
		NSKA retval = new NSKA(archive);
		return retval;
	}
	/** Maps the archive into a recursive Map mimicking the original object
	 * 
	 * @return
	 */
	public Map<String, Object> asMap()
	{
		return asMap(rootObject);
	}
	/** Maps the archive into an newly created object of class T.
	 * 
	 * The mapped class must obey the following conventions:
	 * 1. There must be a no-argument constructor defined
	 * 2. All fields are accessible through setters following the javabeans
	 * convention
	 * 3. All types are either simple types (String, Double, Integer, Boolean,
	 * Map<String, Object>)
	 * 
	 * At this stage sub-objects are not supported but it would simple to do so.
	 * 
	 * @param clazz Class of the object to create and return.
	 * @return
	 * @throws InstantiationException
	 * @throws IllegalAccessException
	 * @throws IllegalArgumentException
	 * @throws InvocationTargetException
	 * @throws NoSuchMethodException
	 * @throws SecurityException
	 */
	public <T> T as(Class<T> clazz) throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException
	{
		return as(rootObject, clazz);
	}
	/** Fills an object instance's fields with the archive contents
	 * 
	 * @param target Object to fill
	 * @return the filled object
	 * 
	 * @throws NoSuchMethodException
	 * @throws SecurityException
	 * @throws IllegalAccessException
	 * @throws IllegalArgumentException
	 * @throws InvocationTargetException
	 */
	public <T> T into(T target) throws NoSuchMethodException, SecurityException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		return into(rootObject, target);
	}

	/** Parse the archive dictionary
	 * 
	 * We look at the following elements:
	 * - The $top dictionary - it contains the top-level structure of the
	 * object archive. It is only a structure - the actual contents are
	 * retrieved from elsewhere
	 * - The $objects array - which contains all object fields in a linear
	 * array. These are the actual contents being referred to by the object
	 * archives.
	 * - The root dictionary - actual root object structure.
	 * 
	 * The $archiver field is located but not used - in theory we should check
	 * it matches an expected value.
	 * 
	 * @param archive Object archive to inflate
	 */
	private NSKA(NSDictionary archive) {
		NSDictionary top = (NSDictionary) archive.get("$top");
		@SuppressWarnings("unused")
		String archiver = archive.get("$archiver").toString();
		data = (NSArray)archive.get("$objects");
		rootObject = (NSDictionary) getValueAt(top.get("root"));
	}

	/** Transforms the structure dictionary into a Map
	 * 
	 * @param spec structure dictionary - such as the rootObject
	 * @return
	 */
	private Map<String, Object> asMap(NSDictionary spec) {
		// We locate the two required arrays in the structure 
		// This is the array of keys pointers - actual keys are located in the
		// data array.
		NSArray keys = (NSArray) spec.get("NS.keys");
		// This is the array of value pointers - actual values are located in
		// the data array.
		NSArray values = (NSArray) spec.get("NS.objects");
		// Normally both arrays should be the same size
		assert keys.count() == values.count();
		
		Map<String, Object> items = new HashMap<>();
		// Now we go through each key and value
		for(int i = 0; i<keys.count(); ++i)
		{
			// Key
			NSString key = (NSString)getValueAt(keys.objectAtIndex(i));
			// Value
			Object value = objectForNS(getValueAt(values.objectAtIndex(i)));
			// Add to the return map
			items.put(key.toString(), value);
			logger.debug("{} = ({})={}", key.toString(), value.getClass().getName(), value.toString());
		}
		return items;
	}
	/** Transform the structure dictionary into a new instance of clazz
	 * 
	 * @param spec structure dictionary - such as the rootObject
	 * @param clazz type of the object to create and fill
	 * @return new instance of the object class
	 * 
	 * @throws InstantiationException
	 * @throws IllegalAccessException
	 * @throws IllegalArgumentException
	 * @throws InvocationTargetException
	 * @throws NoSuchMethodException
	 * @throws SecurityException
	 */
	private <T> T as(NSDictionary spec, Class<T> clazz) throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException
	{
		T data = clazz.getConstructor().newInstance();
		return into(spec, data);
	}
	/** Fill the target object with the structure dictionnary
	 * 
	 * @param spec structure dictionary - such as the rootObject
	 * @param target object to be filled
	 * @return filled object
	 *
	 * @throws NoSuchMethodException
	 * @throws SecurityException
	 * @throws IllegalAccessException
	 * @throws IllegalArgumentException
	 * @throws InvocationTargetException
	 */
	private <T> T into(NSDictionary spec, T target) throws NoSuchMethodException, SecurityException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		Class<? extends Object> clazz = target.getClass(); 
		
		// Not used - could be employed to determine the type of the serialised object at runtime
		@SuppressWarnings("unused")
		String className = getClassName(spec);
	
		// First transform the spec into a recursive map
		Map<String, Object> items=asMap(spec);
		
		// Now go through all keys in the map
		for(Entry<String, Object> field: items.entrySet())
		{
			// Key name
			String name = field.getKey();
			// Transform into CamelCase so we can form the setter method name
			name = name.replaceFirst(name.substring(0,1), name.substring(0,1).toUpperCase());
			String setterName = "set"+name;
			// Get the value and type of the parameter
			Object o = field.getValue();
			Class<? extends Object> fieldType = o.getClass();
			// First we try for an exact match. This will throw unless an exact
			// match is found - for example if o is an HashMap and our setter
			// takes a Map then this will throw.
			try{
				Method setter = clazz.getMethod(setterName, fieldType);
				setter.invoke(target, o);
			}
			catch(NoSuchMethodException e)
			{
				// Exact match failed, we'll go round the possible setters
				boolean found = false;
				for(Method m: clazz.getMethods())
				{
					// Prototype should be setValue(Object val) or so - exact
					// method name, one parameter
					if(setterName.equals(m.getName()) && 1==m.getParameterTypes().length)
					{
						// If the method can accept our parameter we invoke it
						if(fieldType.isInstance(o))
						{
							m.invoke(target, o);
							found = true;
							break;
						}
					}
				}
				// We did not find a direct match. Perhaps we could try harder.
				// TODO try harder finding a match with a conversion
				if(!found)
					throw e;
			}
		}
		return target;
	}
	/**	Logger. */
	private static final Logger logger = LoggerFactory.getLogger(NSKA.class);
	/** Data items. While walking the root object structure we'll encounter
	 * UID objects the value of which point to entries in this array.
	 *  */
	private NSArray data;
	/** Root object structure */
	private NSDictionary rootObject;
	/** Obtains the entry pointed to by the relevant UID object
	 * 
	 * @param id
	 * @return
	 */
	private NSObject getValueAt(NSObject id)
	{
		UID root = (UID)id;
		byte b = root.getBytes()[0];
		
		return data.objectAtIndex(b);
		
	}
	/** Obtains the name of the class serialised in the given structure
	 * 
	 * @param spec
	 * @return
	 */
	private String getClassName(NSDictionary spec) {
		NSDictionary classHierarchy = (NSDictionary) getValueAt(spec.get("$class"));
		NSArray classes = (NSArray)classHierarchy.get("$classes");
		return classes.objectAtIndex(0).toString();
	}

	/** Maps a NSObject value into the equivalent Java type.
	 * 
	 * @param value
	 * @return
	 */
	private Object objectForNS(NSObject value) {
		switch(value.getClass().getSimpleName())
		{
		case "NSArray":
		{
			// Note: untested
			NSArray val = ((NSArray)value);
			Object[] retval = new Object[val.count()];
			for(int i = 0; i<val.count();++i)
			{
				retval[i]=objectForNS(val.objectAtIndex(i));
			}
			return retval;
		}
		case "NSData":
		{
			NSData val = (NSData)value;
			return val.bytes();
		}
		case "NSDate":
		{
			NSDate val = (NSDate)value;
			return val.getDate();
		}
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
			else
			{
				return d.getHashMap();
			}
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
			break;
		}
		case "NSSet":
		{
			NSSet val = (NSSet)value;
			Set<Object> retval = new HashSet<>();
			for(NSObject v: val.allObjects())
			{
				retval.add(objectForNS(v));
			}
			return retval;
		}
		case "NSString":
			return ((NSString)value).toString();
		case "UID":
			return ((UID)value).getBytes();
		default:
			return value;
		}
		return value;
	}
}
