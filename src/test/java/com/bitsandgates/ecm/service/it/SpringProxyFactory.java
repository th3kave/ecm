package com.bitsandgates.ecm.service.it;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import com.bitsandgates.ecm.ProxyFactory;

@Component
public class SpringProxyFactory implements ProxyFactory {

    @Autowired 
    private ApplicationContext context;
    
    @Override
    public Object proxy(Object obj) {
        return context.getAutowireCapableBeanFactory().applyBeanPostProcessorsAfterInitialization(obj, obj.getClass().getName());
    }
}
