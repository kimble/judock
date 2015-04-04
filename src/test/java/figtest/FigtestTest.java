package figtest;

import com.developerb.judock.JUDock;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Example stolen from Fig.
 * Simple Python web application linked Redis.
 */
public class FigtestTest {

    @Rule
    public JUDock docker = new JUDock();

    @Test
    public void pythonWebContainerLinkedToRedis() throws Exception {
        RedisContainerFactory.Container redisContainer = docker.manage (
                new RedisContainerFactory()
        );

        WebContainerFactory.Container webContainer = docker.manage (
                new WebContainerFactory(redisContainer)
        );

        assertEquals("Hello... I have been seen 2 times.", webContainer.fetchHtml());
        assertEquals("Hello... I have been seen 3 times.", webContainer.fetchHtml());
    }

}
