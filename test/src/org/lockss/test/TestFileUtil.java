/*
 * $Id: TestFileUtil.java,v 1.6 2003-07-19 00:05:28 troberts Exp $
 */

/*

Copyright (c) 2000-2003 Board of Trustees of Leland Stanford Jr. University,
all rights reserved.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.  IN NO EVENT SHALL
STANFORD UNIVERSITY BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

Except as contained in this notice, the name of Stanford University shall not
be used in advertising or otherwise to promote the sale, use or other dealings
in this Software without prior written authorization from Stanford University.

*/

package org.lockss.test;

import java.util.*;
import java.io.*;
import junit.framework.TestCase;
import org.lockss.test.*;
import org.lockss.util.*;


/**
 * test class for org.lockss.util.TestFileUtil
 */

public class TestFileUtil extends LockssTestCase {
  public static Class testedClasses[] = {
    org.lockss.test.FileUtil.class
  };


  public TestFileUtil(String msg){
    super(msg);
  }

  public void testTempFile() throws IOException {
    String testStr = "test string";
    File file = FileUtil.tempFile("prefix");
    FileWriter fw = new FileWriter(file);
    fw.write(testStr);
    fw.close();
    assertTrue(file.exists());
    FileReader fr = new FileReader(file);
    char in[] = new char[40];
    int len = fr.read(in);
    assertEquals(len, testStr.length());
    String res = new String(in, 0, len);
    assertEquals(testStr, res);
  }

  public void testWriteTempFile() throws IOException {
    String testStr = "multi-line\ntest string\n";
    File file = FileUtil.writeTempFile("prefix", testStr);
    assertTrue(file.exists());
    FileReader fr = new FileReader(file);
    char in[] = new char[80];
    int len = fr.read(in);
    assertEquals(len, testStr.length());
    String res = new String(in, 0, len);
    assertEquals(testStr, res);
  }

  public void testTempDir() throws IOException {
    try {
      File dir = FileUtil.createTempDir("pre", "suff", new File("/nosuchdir"));
      fail("Shouldn't be able to create temp dir in /nosuchdir");
    } catch (IOException e) {
    }
    File dir = FileUtil.createTempDir("pre", "suff");
    assertTrue(dir.exists());
    assertTrue(dir.isDirectory());
    assertEquals(0, dir.listFiles().length);
    File f = new File(dir, "foo");
    assertFalse(f.exists());
    assertTrue(f.createNewFile());
    assertTrue(f.exists());
    assertEquals(1, dir.listFiles().length);
    assertEquals("foo", dir.listFiles()[0].getName());
    assertTrue(f.delete());
    assertEquals(0, dir.listFiles().length);
    assertTrue(dir.delete());
    assertFalse(dir.exists());
  }

  public void testDelTree() throws IOException {
    File dir = FileUtil.createTempDir("deltree", null);
    File d1 = new File(dir, "foo");
    assertTrue(d1.mkdir());
    File d2 = new File(d1, "bar");
    assertTrue(d2.mkdir());
    assertTrue(new File(dir, "f1").createNewFile());
    assertTrue(new File(d1, "d1f1").createNewFile());
    assertTrue(new File(d2, "d2f1").createNewFile());
    assertFalse(dir.delete());
    assertTrue(FileUtil.delTree(dir));
    assertFalse(dir.exists());
  }

  public void testEnumerateFilesNullFile() {
    try {
      FileUtil.enumerateFiles(null);
      fail("FileUtil.enumerateFiles() should have thrown when a null file "
	   +"was specified");
    } catch (IllegalArgumentException e) {
    }
  }

  public void testEnumerateFilesOneFile() {
    File file = new MockFile("blah");
    assertEquals(ListUtil.list(file), FileUtil.enumerateFiles(file));
  }

  public void testEnumerateFilesDirectory() {
    MockFile file = new MockFile("Path_to_directory");
    MockFile child1 = new MockFile("child1");
    MockFile child2 = new MockFile("child2");

    file.setIsDirectory(true);
    file.setChild(child1);
    file.setChild(child2);

    assertEquals(ListUtil.list(child1, child2), FileUtil.enumerateFiles(file));
  }

  public void testEnumerateFilesMultiLevelDirectory() {
    MockFile file = new MockFile("Path_to_directory");
    MockFile child1 = new MockFile("child1");
    MockFile child2 = new MockFile("child2");
    MockFile childDir1 = new MockFile("childDir1");
    MockFile child11 = new MockFile("child11");
    

    file.setIsDirectory(true);
    file.setChild(child1);
    file.setChild(child2);
    file.setChild(childDir1);

    childDir1.setIsDirectory(true);
    childDir1.setChild(child11);

    assertEquals(ListUtil.list(child1, child2, child11),
		 FileUtil.enumerateFiles(file));
  }

  public void testGetPathUnderRootNullParams() {
    try {
      FileUtil.getPathUnderRoot(null, null);
      fail("FileUtil.getPathUnderRoot() should have thrown when a null file "
	   +"was specified");
    } catch (IllegalArgumentException e) {
    }
    try {
      FileUtil.getPathUnderRoot(new File("blah"), null);
      fail("FileUtil.getPathUnderRoot() should have thrown when a null file "
	   +"was specified");
    } catch (IllegalArgumentException e) {
    }
    try {
      FileUtil.getPathUnderRoot(null, new File("blah"));
      fail("FileUtil.getPathUnderRoot() should have thrown when a null file "
	   +"was specified");
    } catch (IllegalArgumentException e) {
    }
  }

  public void testGetPathUnderRootTrailingSlash() {
    File src = new MockFile("/tmp/dir/test/file/path");
    File root = new MockFile("/tmp/dir/");
    assertEquals("test/file/path", FileUtil.getPathUnderRoot(src, root));
  }
  
  public void testGetPathUnderRootNoCommonPath() {
    File src = new File("/tmp/dir/test/file/path");
    File root = new File("/other/directory");
    assertNull(FileUtil.getPathUnderRoot(src, root));
  }
}


