package org.carlspring.strongbox.artifact.coordinates;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.net.URI;

import org.junit.Test;

/**
 * @author sbespalov
 *
 */
public class NpmArtifactCoorinatesTest
{

    @Test
    public void testArtifactPathToCoordinatesConversion()
    {
        NpmArtifactCoordinates c = NpmArtifactCoordinates.parse("react-redux/react-redux/5.0.6/react-redux-5.0.6.tgz");

        assertNull(c.getScope());
        assertEquals("react-redux", c.getName());
        assertEquals("5.0.6", c.getVersion());

        c = NpmArtifactCoordinates.parse("@types/node/8.0.51/node-8.0.51.tgz");

        assertEquals("@types", c.getScope());
        assertEquals("node", c.getName());
        assertEquals("8.0.51", c.getVersion());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testVersionAssertion()
    {
        NpmArtifactCoordinates.parse("@types/node/8.beta1/node-8.beta1.tgz");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNameAssertion()
    {
        NpmArtifactCoordinates.parse("@types/NODE/8.0.51/node-8.0.51.tgz");
    }

    @Test
    public void testOfUri()
    {
        NpmArtifactCoordinates c = NpmArtifactCoordinates.of(URI.create("@carlspring/npm-test-release/-/npm-test-release-1.0.0.tgz"));

        assertEquals("@carlspring", c.getScope());
        assertEquals("npm-test-release", c.getName());
        assertEquals("1.0.0", c.getVersion());    }
}
