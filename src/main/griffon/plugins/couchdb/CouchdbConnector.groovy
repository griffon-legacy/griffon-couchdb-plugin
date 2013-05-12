/*
 * Copyright 2011-2013 the original author or authors.
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

package griffon.plugins.couchdb

import griffon.core.GriffonApplication
import griffon.util.GriffonNameUtils
import griffon.util.ConfigUtils

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import org.apache.http.auth.AuthScope
import org.apache.http.auth.UsernamePasswordCredentials
import org.codehaus.griffon.couchdb.json.JsonConverterUtils
import org.codehaus.griffon.couchdb.json.JsonDateConverter
import org.jcouchdb.db.Database
import org.svenson.JSON
import org.svenson.JSONConfig
import org.svenson.JSONParser
import org.svenson.converter.DefaultTypeConverterRepository

/**
 * @author Andres Almiray
 */
@Singleton
final class CouchdbConnector {
    private static final String DEFAULT = 'default'
    private static final Logger LOG = LoggerFactory.getLogger(CouchdbConnector)
    private bootstrap

    ConfigObject createConfig(GriffonApplication app) {
        if (!app.config.pluginConfig.couchdb) {
            app.config.pluginConfig.couchdb = ConfigUtils.loadConfigWithI18n('CouchdbConfig')
        }
        app.config.pluginConfig.couchdb
    }

    private ConfigObject narrowConfig(ConfigObject config, String databaseName) {
        if (config.containsKey('database') && databaseName == DEFAULT) {
            return config.database
        } else if (config.containsKey('databases')) {
            return config.databases[databaseName]
        }
        return config
    }

    Database connect(GriffonApplication app, ConfigObject config, String databaseName = DEFAULT) {
        if (DatabaseHolder.instance.isDatabaseConnected(databaseName)) {
            return DatabaseHolder.instance.getDatabase(databaseName)
        }

        config = narrowConfig(config, databaseName)
        app.event('CouchdbConnectStart', [config, databaseName])
        Database database = startCouchdb(app, config, databaseName)
        DatabaseHolder.instance.setDatabase(databaseName, database)
        bootstrap = app.class.classLoader.loadClass('BootstrapCouchdb').newInstance()
        bootstrap.metaClass.app = app
        resolveCouchdbProvider(app).withCouchdb { dn, d -> bootstrap.init(dn, d) }
        app.event('CouchdbConnectEnd', [databaseName, database])
        database
    }

    void disconnect(GriffonApplication app, ConfigObject config, String databaseName = DEFAULT) {
        if (DatabaseHolder.instance.isDatabaseConnected(databaseName)) {
            config = narrowConfig(config, databaseName)
            Database database = DatabaseHolder.instance.getDatabase(databaseName)
            app.event('CouchdbDisconnectStart', [config, databaseName, database])
            resolveCouchdbProvider(app).withCouchdb { dn, d -> bootstrap.destroy(dn, d) }
            app.event('CouchdbDisconnectEnd', [config, databaseName])
            DatabaseHolder.instance.disconnectDatabase(databaseName)
        }
    }

    CouchdbProvider resolveCouchdbProvider(GriffonApplication app) {
        def couchdbProvider = app.config.couchdbProvider
        if (couchdbProvider instanceof Class) {
            couchdbProvider = couchdbProvider.newInstance()
            app.config.couchdbProvider = couchdbProvider
        } else if (!couchdbProvider) {
            couchdbProvider = DefaultCouchdbProvider.instance
            app.config.couchdbProvider = couchdbProvider
        }
        couchdbProvider
    }

    private Database startCouchdb(GriffonApplication app, ConfigObject config, String databaseName) {
        String host = config?.host ?: 'localhost'
        Integer port = config?.port ?: 5984
        String database = config?.datastore ?: app.metadata['app.name']
        String username = config?.username ?: ''
        String password = config?.password ?: ''

        String realm = config?.realm ?: null
        String scheme = config?.scheme ?: null

        Database db = new Database(host, port, database)

        // check to see if there are any user credentials and set them
        if (!GriffonNameUtils.isBlank(username)) {
            def credentials = new UsernamePasswordCredentials(username, password)
            def authScope = new AuthScope(host, port)

            // set the realm and scheme if they are set
            if (!GriffonNameUtils.isBlank(realm) || !GriffonNameUtils.isBlank(scheme)) {
                authScope = new AuthScope(host, port, realm, scheme)
            }

            db.server.setCredentials(authScope, credentials)
        }

        DefaultTypeConverterRepository typeConverterRepository = new DefaultTypeConverterRepository()
        JsonDateConverter dateConverter = new JsonDateConverter()
        typeConverterRepository.addTypeConverter(dateConverter)

        JSON generator = new JSON()
        generator.setIgnoredProperties(Arrays.asList('metaClass'))
        generator.setTypeConverterRepository(typeConverterRepository)
        generator.registerTypeConversion(java.util.Date, dateConverter)
        generator.registerTypeConversion(java.sql.Date, dateConverter)
        generator.registerTypeConversion(java.sql.Timestamp, dateConverter)

        JSONParser parser = new JSONParser()
        parser.setTypeConverterRepository(typeConverterRepository)
        parser.registerTypeConversion(java.util.Date, dateConverter)
        parser.registerTypeConversion(java.sql.Date, dateConverter)
        parser.registerTypeConversion(java.sql.Timestamp, dateConverter)
        app.event('ConfigureCouchdbJSONParser', [config, databaseName, parser])

        db.jsonConfig = new JSONConfig(generator, parser)

        // setup views
        ClasspathCouchDBUpdater updater = new ClasspathCouchDBUpdater()
        updater.setDatabase(db)
        updater.updateDesignDocuments()

        db
    }
}