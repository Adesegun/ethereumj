package org.ethereum.config;

import com.google.common.io.Files;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;
import org.ethereum.util.FileUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import static org.ethereum.config.Initializer.IncompatibleDatabaseHandler.Behavior;
import static org.ethereum.config.Initializer.IncompatibleDatabaseHandler.Behavior.*;

/**
 * Created by Stan Reshetnyk on 11.09.16.
 */
public class InitializerTest {

    final Initializer.IncompatibleDatabaseHandler resetHelper = new Initializer.IncompatibleDatabaseHandler();

    File tempFile;
    String databaseDir;
    File versionFile;

    @Before
    public void before() {
        tempFile = Files.createTempDir();
        databaseDir = tempFile.getAbsolutePath() + "/database";
        versionFile = new File(databaseDir + "/version.properties");
    }

    @After
    public void after() {
        FileUtil.recursiveDelete(tempFile.getAbsolutePath());
    }

    // RESET

    @Test
    public void helper_shouldCreateVersionFile() {
        SystemProperties props = withConfig(1, null);

        // state without database
        assertEquals(new Integer(-1), resetHelper.getDatabaseVersion(versionFile));
        assertTrue(!resetHelper.isDatabaseDirectoryExists(props));

        // create database version file
        resetHelper.process(props);

        // state with just created database
        assertEquals(new Integer(1), resetHelper.getDatabaseVersion(versionFile));
        assertTrue(resetHelper.isDatabaseDirectoryExists(props));

        // running process for a second time should change nothing
        resetHelper.process(props);
        assertEquals(new Integer(1), resetHelper.getDatabaseVersion(versionFile));
        assertTrue(resetHelper.isDatabaseDirectoryExists(props));
    }

    @Test
    public void helper_shouldCreateVersionFile_whenOldVersion() {
        // create database without version
        SystemProperties props1 = withConfig(1, null);
        resetHelper.process(props1);
        versionFile.renameTo(new File(versionFile.getAbsoluteFile() + ".renamed"));

        SystemProperties props2 = withConfig(2, IGNORE);
        resetHelper.process(props2);

        assertEquals(new Integer(1), resetHelper.getDatabaseVersion(versionFile));
        assertTrue(resetHelper.isDatabaseDirectoryExists(props2));
    }

    @Test(expected = Error.class)
    public void helper_shouldStop_whenNoVersionFileAndNotFirstVersion() throws IOException {
        SystemProperties props = withConfig(2, EXIT);
        resetHelper.process(props);

        // database is assumed to exist if dir is not empty
        versionFile.renameTo(new File(versionFile.getAbsoluteFile() + ".renamed"));

        resetHelper.process(props);
    }

    @Test
    public void helper_shouldReset_whenDifferentVersionAndFlag() {
        SystemProperties props1 = withConfig(1, null);
        resetHelper.process(props1);

        SystemProperties props2 = withConfig(2, RESET);
        resetHelper.process(props2);
        assertTrue(!resetHelper.isDatabaseDirectoryExists(props2));
    }

    @Test(expected = Error.class)
    public void helper_shouldExit_whenDifferentVersionAndFlag() {
        final SystemProperties props1 = withConfig(1, null);
        resetHelper.process(props1);

        final SystemProperties props2 = withConfig(2, EXIT);
        resetHelper.process(props2);
    }

    @Test(expected = Error.class)
    public void helper_shouldExit_byDefault() {
        final SystemProperties props1 = withConfig(1, null);
        resetHelper.process(props1);

        final SystemProperties props2 = withConfig(2, null);
        resetHelper.process(props2);
    }

    @Test
    public void helper_shouldIgnore_whenDifferentVersionAndFlag() {
        final SystemProperties props1 = withConfig(1, EXIT);
        resetHelper.process(props1);

        final SystemProperties props2 = withConfig(2, IGNORE);
        resetHelper.process(props2);
        assertTrue(resetHelper.isDatabaseDirectoryExists(props2));
        assertEquals(new Integer(1), resetHelper.getDatabaseVersion(versionFile));
    }


    // HELPERS

    private SystemProperties withConfig(int databaseVersion, Behavior behavior) {
        Config config = ConfigFactory.empty();

        if (behavior != null) {
            config = config.withValue("database.incompatibleDatabaseBehavior",
                    ConfigValueFactory.fromAnyRef(behavior.toString().toLowerCase()));
        }


        SPO systemProperties = new SPO(config);
        systemProperties.setDataBaseDir(databaseDir);
        systemProperties.setDatabaseVersion(databaseVersion);
        return systemProperties;
    }

    public static class SPO extends SystemProperties {

        public SPO(Config config) {
            super(config);
        }

        public void setDatabaseVersion(Integer databaseVersion) {
            this.databaseVersion = databaseVersion;
        }
    }
}
