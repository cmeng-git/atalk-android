package java.beans;

import java.lang.reflect.Method;

public class PropertyDescriptor
{
    private Class<?> objectClass;
    private String name;

    public PropertyDescriptor(String name, Class<?> objectClass)
    {
        this.name = name;
        this.objectClass = objectClass;
    }

    public Class<?> getPropertyType()
    {
        try {
            return this.objectClass.getMethod("get" + Character.toUpperCase(this.name.charAt(0)) + this.name.substring(1), new Class[0]).getReturnType();
        } catch (SecurityException e) {
            return null;
        } catch (NoSuchMethodException e) {
        }
        return null;
    }

    public Method getWriteMethod()
    {
        try {
            return this.objectClass.getMethod("set" + Character.toUpperCase(this.name.charAt(0)) + this.name.substring(1), new Class[]{getPropertyType()});
        } catch (SecurityException e) {
            return null;
        } catch (NoSuchMethodException e) {
        }
        return null;
    }
}