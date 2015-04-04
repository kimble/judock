package figtest;

import com.developerb.judock.JUDock;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;


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

        assertEquals("Hello... I have been seen 2 times.", webContainer.httpGet("http://%s"));
        assertEquals("Hello... I have been seen 3 times.", webContainer.httpGet("http://%s"));
    }

}
