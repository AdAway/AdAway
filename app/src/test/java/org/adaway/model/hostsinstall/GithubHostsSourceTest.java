package org.adaway.model.hostsinstall;

import org.junit.Test;

import java.net.MalformedURLException;

import static org.junit.Assert.*;

public class GithubHostsSourceTest {

    @Test
    public void isHostedOnGithub() {
        assertTrue("Failed to detect GitHub hosting", GithubHostsSource.isHostedOnGithub("https://raw.githubusercontent.com/EnergizedProtection/block/master/spark/formats/hosts"));
    }

    @Test
    public void getLastUpdate() {

        String url ="https://raw.githubusercontent.com/EnergizedProtection/block/master/spark/formats/hosts";
        try {
            GithubHostsSource githubHostsSource = new GithubHostsSource(url);
            System.out.println(githubHostsSource.getLastUpdate());
        } catch (MalformedURLException e) {
            fail("Fail to create GitHub hosts source");
        }

    }
}