package org.opensolaris.opengrok.web.suggester;

import org.glassfish.jersey.internal.inject.AbstractBinder;
import org.opensolaris.opengrok.web.suggester.provider.service.SuggesterService;
import org.opensolaris.opengrok.web.suggester.provider.service.impl.SuggesterServiceImpl;

public class SuggesterAppBinder extends AbstractBinder {

    @Override
    protected void configure() {
        bind(SuggesterServiceImpl.getInstance()).to(SuggesterService.class);
    }

}
