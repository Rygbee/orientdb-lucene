/*
 *
 *  * Copyright 2014 Orient Technologies.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *      http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *  
 */

package com.orientechnologies.test;

import com.orientechnologies.orient.core.db.document.ODatabaseDocument;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.index.OIndex;
import com.orientechnologies.orient.core.intent.OIntentMassiveInsert;
import com.orientechnologies.orient.core.metadata.schema.OClass;
import com.orientechnologies.orient.core.metadata.schema.OSchema;
import com.orientechnologies.orient.core.metadata.schema.OType;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.sql.OCommandSQL;
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Created by enricorisa on 28/06/14.
 */

@Test(groups = "embedded")
public class LuceneInsertReadMultithreadTest extends BaseLuceneTest {

    private final static int THREADS = 1;
    private final static int RTHREADS = 1;
    private final static int CYCLE = 1000;

    protected String url = "";

    public LuceneInsertReadMultithreadTest() {
        super();
    }

    public LuceneInsertReadMultithreadTest(boolean remote) {
        super(remote);
    }

    @Test(enabled = false)
    public class LuceneInsertThread implements Runnable {

        private ODatabaseDocumentTx db;
        private int cycle = 0;
        private int commitBuf = 500;

        public LuceneInsertThread(int cycle) {
            this.cycle = cycle;
        }

        @Override
        public void run() {

            db = new ODatabaseDocumentTx(url);
            db.open("admin", "admin");
            db.declareIntent(new OIntentMassiveInsert());
            db.begin();
            for (int i = 0; i < cycle; i++) {
                ODocument doc = new ODocument("City");

                doc.field("name", "Rome");

                db.save(doc);
                if (i % commitBuf == 0) {
                    db.commit();
                    db.begin();
                }

            }

            db.close();
        }
    }

    public class LuceneReadThread implements Runnable {
        private final int cycle;
        private ODatabaseDocument databaseDocumentTx;

        public LuceneReadThread(int cycle) {
            this.cycle = cycle;
        }

        @Override
        public void run() {

            databaseDocumentTx = new ODatabaseDocumentTx(url);
            databaseDocumentTx.open("admin", "admin");
            OSchema schema = databaseDocumentTx.getMetadata().getSchema();
            OIndex idx = schema.getClass("City").getClassIndex("City.name");

            for (int i = 0; i < cycle; i++) {

                databaseDocumentTx.command(new OSQLSynchQuery<ODocument>("select from city where name LUCENE 'Rome'")).execute();

            }

        }
    }

    @Override
    protected String getDatabaseName() {
        return "multiThread";
    }

    @BeforeClass
    public void init() {
        initDB();

        url = databaseDocumentTx.getURL();
        OSchema schema = databaseDocumentTx.getMetadata().getSchema();
        OClass oClass = schema.createClass("City");

        oClass.createProperty("name", OType.STRING);
        databaseDocumentTx.command(new OCommandSQL("create index City.name on City (name) FULLTEXT ENGINE LUCENE")).execute();
    }

    @AfterClass
    public void deInit() {
        deInitDB();
    }

    @Test
    public void testConcurrentInsertWithIndex() throws Exception {


        Thread[] threads = new Thread[THREADS + RTHREADS];
        for (int i = 0; i < THREADS; ++i)
            threads[i] = new Thread(new LuceneInsertThread(CYCLE), "ConcurrentWriteTest" + i);

        for (int i = THREADS; i < THREADS + RTHREADS; ++i)
            threads[i] = new Thread(new LuceneReadThread(CYCLE), "ConcurrentReadTest" + i);

        for (int i = 0; i < THREADS + RTHREADS; ++i)
            threads[i].start();

        for (int i = 0; i < THREADS + RTHREADS; ++i)
            threads[i].join();

        databaseDocumentTx.reload();
        databaseDocumentTx.getMetadata().getIndexManager().reload();
        databaseDocumentTx.close();
        databaseDocumentTx.open("admin", "admin");
        OIndex idx = databaseDocumentTx.getMetadata().getSchema().getClass("City").getClassIndex("City.name");

        Assert.assertEquals(idx.getSize(), THREADS * CYCLE);
    }
}
