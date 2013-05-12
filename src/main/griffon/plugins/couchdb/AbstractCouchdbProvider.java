/*
 * Copyright 2012-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package griffon.plugins.couchdb;

import griffon.util.CallableWithArgs;
import griffon.exceptions.GriffonException;
import groovy.lang.Closure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.jcouchdb.db.Database;

import static griffon.util.GriffonNameUtils.isBlank;

/**
 * @author Andres Almiray
 */
public abstract class AbstractCouchdbProvider implements CouchdbProvider {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractCouchdbProvider.class);
    private static final String DEFAULT = "default";

    public <R> R withCouchdb(Closure<R> closure) {
        return withCouchdb(DEFAULT, closure);
    }

    public <R> R withCouchdb(String databaseName, Closure<R> closure) {
        if (isBlank(databaseName)) databaseName = DEFAULT;
        if (closure != null) {
            Database database = getDatabase(databaseName);
            if (LOG.isDebugEnabled()) {
                LOG.debug("Executing statement on databaseName '" + databaseName + "'");
            }
            return closure.call(databaseName, database);
        }
        return null;
    }

    public <R> R withCouchdb(CallableWithArgs<R> callable) {
        return withCouchdb(DEFAULT, callable);
    }

    public <R> R withCouchdb(String databaseName, CallableWithArgs<R> callable) {
        if (isBlank(databaseName)) databaseName = DEFAULT;
        if (callable != null) {
            Database database = getDatabase(databaseName);
            if (LOG.isDebugEnabled()) {
                LOG.debug("Executing statement on databaseName '" + databaseName + "'");
            }
            callable.setArgs(new Object[]{databaseName, database});
            return callable.call();
        }
        return null;
    }

    protected abstract Database getDatabase(String databaseName);
}