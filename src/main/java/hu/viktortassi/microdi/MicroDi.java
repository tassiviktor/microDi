package hu.viktortassi.microdi;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

/**
 * Based on github: https://github.com/lestard/EasyDI
 * @author tassiviktor
 * @version 1.0
 */
public class MicroDi {
     /**
     * This map stores the implementation type (value) that should be used for
     * an interface type (key).
     */
    private final Map<Class, Class> interfaceMappings = new HashMap<>();
    /**
     * This map stores providers for given class types.
     */
    private Map<Class, Provider> providers = new HashMap<>();
    /**
     * A set of classes that are marked to be treated as singleton even if they
     * aren't annotated as singleton.
     */
    private Set<Class> singletonClasses = new HashSet<>();
    /**
     * A map with all classes that are marked as singleton and the actual
     * singleton instance.
     */
    private Map<Class, Object> singletonInstances = new HashMap<>();

    public <T> void bindInterface(Class<T> interfaceType, Class<? extends T> implementationType) {
        if (interfaceType.isInterface()) {

            if (implementationType.isInterface() || isAbstractClass(implementationType)) {
                throw new IllegalArgumentException("Expecting the second argument to be an actual implementing class");
            } else {
                interfaceMappings.put(interfaceType, implementationType);
            }

        } else {
            throw new IllegalArgumentException("Expecting the first argument to be an interface.");
        }
    }

    public <T> void bindInstance(Class<T> classType, T instance) {
        bindProvider(classType, () -> instance);
    }

    public <T> void bindProvider(Class<T> classType, Provider<T> provider) {
        providers.put(classType, provider);
    }

    private <T> T getInstanceFromProvider(Class<T> type) {
        try {
            final Provider<T> provider = providers.get(type);
            return provider.get();
        } catch (Exception e) {
            throw new MicroDiException("An Exception was thrown by the provider.", e);
        }
    }

    public <T> T getInstance(Class<T> requestedType) {

        if (isAbstractClass(requestedType)) {
            if (providers.containsKey(requestedType)) {
                return getInstanceFromProvider(requestedType);
            } else {
                throw new MicroDiException("Missing provider for abstract class: " + requestedType.getCanonicalName());
            }
        }
        
        Class<T> type = requestedType;

        if (requestedType.isInterface()) {
            if (interfaceMappings.containsKey(requestedType)) {
                // replace the interface type with the implementing class type.
                type = interfaceMappings.get(requestedType);
            } else if (providers.containsKey(requestedType)) {
                return getInstanceFromProvider(requestedType);
            } else {
                throw new MicroDiException("No provider for Interface: " + requestedType.getCanonicalName());
            }
        }

        if (singletonInstances.containsKey(type)) {
            return (T) singletonInstances.get(type);
        }

        if (providers.containsKey(type)) {
            final T instanceFromProvider = getInstanceFromProvider(type);
            if (isSingleton(type) && !singletonClasses.contains(type)) {
                singletonInstances.put(type, instanceFromProvider);
            }
            return instanceFromProvider;
        }
        return createNewInstance(type);
    }

    private <T> T createNewInstance(Class<T> type) {
        try {
            final T newInstance = type.newInstance();
            if (isSingleton(type)) {
                singletonInstances.put(type, newInstance);
            }
            inject(newInstance);
            callPostConstruct(newInstance);
            return newInstance;
        } catch (IllegalAccessException | IllegalArgumentException | InstantiationException | InvocationTargetException ex) {
            throw new MicroDiException(ex.getMessage(), ex);
        }
    }

    private <T> void callPostConstruct(T newInstance) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException{
        for (Method method : newInstance.getClass().getMethods()) {
                if (method.isAnnotationPresent(PostConstruct.class)) {
                    method.setAccessible(true);
                    method.invoke(newInstance);
                }
            }
    }
    
    public <T> void inject(T newInstance) throws IllegalArgumentException, IllegalAccessException {
        Class superClass = newInstance.getClass();
        while (superClass != null) {
            for (Field field : superClass.getDeclaredFields()) {
                if (field.isAnnotationPresent(Inject.class)) {
                    field.setAccessible(true);
                    field.set(newInstance, getInstance(field.getType()));
                }
            }
            superClass = superClass.getSuperclass();
        }
    }

    static boolean isAbstractClass(Class type) {
        return !type.isInterface() && Modifier.isAbstract(type.getModifiers());
    }

    private boolean isSingleton(Class type) {
        return type.isAnnotationPresent(Singleton.class) || singletonClasses.contains(type);
    }
}
