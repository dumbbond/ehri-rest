/*
 * Copyright 2020 Data Archiving and Networked Services (an institute of
 * Koninklijke Nederlandse Akademie van Wetenschappen), King's College London,
 * Georg-August-Universitaet Goettingen Stiftung Oeffentlichen Rechts
 *
 * Licensed under the EUPL, Version 1.1 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing
 * permissions and limitations under the Licence.
 */

package eu.ehri.extension.providers;

import eu.ehri.extension.PermissionsResource;
import eu.ehri.project.acl.InheritedGlobalPermissionSet;

import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

@Provider
@Produces(MediaType.APPLICATION_JSON)
public class InheritedGlobalPermissionSetProvider
        implements MessageBodyWriter<InheritedGlobalPermissionSet>, JsonMessageBodyHandler {

    @Override
    public boolean isWriteable(Class<?> aClass, Type type, Annotation[] annotations, MediaType mediaType) {
        return aClass == InheritedGlobalPermissionSet.class;
    }

    @Override
    public long getSize(InheritedGlobalPermissionSet globalPermissionSet, Class<?> aClass, Type type, Annotation[] annotations,
            MediaType mediaType) {
        return -1L;
    }

    @Override
    public void writeTo(InheritedGlobalPermissionSet globalPermissionSet,
            Class<?> aClass, Type type, Annotation[] annotations,
            MediaType mediaType, MultivaluedMap<String, Object> headers,
            OutputStream outputStream) throws IOException, WebApplicationException {
        headers.putSingle(HttpHeaders.CACHE_CONTROL,
                PermissionsResource.getCacheControl().toString());
        mapper.writeValue(outputStream, globalPermissionSet.serialize());
    }
}
