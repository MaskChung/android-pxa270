/*
 This Java source file was generated by test-to-java.xsl
 and is a derived work from the source document.
 The source document contained the following notice:



 Copyright (c) 2001 World Wide Web Consortium, 
 (Massachusetts Institute of Technology, Institut National de
 Recherche en Informatique et en Automatique, Keio University).  All 
 Rights Reserved.  This program is distributed under the W3C's Software
 Intellectual Property License.  This program is distributed in the 
 hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR 
 PURPOSE.  

 See W3C License http://www.w3.org/Consortium/Legal/ for more details.


 */

package org.w3c.dom;

import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * The "getElementsByTagNameNS(namespaceURI,localName)" method for a Document
 * should return a new NodeList of all Elements that have a namespace when local
 * name is specified as ' '.
 * 
 * Invoke method getElementsByTagNameNS(namespaceURI,localName) on this document
 * with namespaceURI and localName as " ". Method should return a new NodeList
 * of 37 elements.
 * 
 * @author NIST
 * @author Mary Brady
 * @see <a
 *      href="http://www.w3.org/TR/DOM-Level-2-Core/core#ID-getElBTNNS">http://www.w3.org/TR/DOM-Level-2-Core/core#ID-getElBTNNS</a>
 */
public final class GetElementsByTagNameNS extends DOMTestCase {
    DOMDocumentBuilderFactory factory;

    DocumentBuilder builder;

    protected void setUp() throws Exception {
        super.setUp();
        try {
            factory = new DOMDocumentBuilderFactory(DOMDocumentBuilderFactory
                    .getConfiguration2());
            builder = factory.getBuilder();
        } catch (Exception e) {
            fail("Unexpected exception" + e.getMessage());
        }
    }

    protected void tearDown() throws Exception {
        factory = null;
        builder = null;
        super.tearDown();
    }

    public void testGetElementsByTagNameNS1() throws Throwable {
        String namespaceURI = "*";
        String localName = "*";
        Document doc;
        NodeList newList;
        doc = (Document) load("staffNS", builder);
        newList = doc.getElementsByTagNameNS(namespaceURI, localName);
        // BEGIN android-changed: Was 37, but that assumed validation.
        assertEquals("throw_Size", 36, newList.getLength());
        // END android-changed
    }

    public void testGetElementsByTagNameNS2() throws Throwable {
        Document doc;
        NodeList newList;
        Element newElement;
        String prefix;
        String lname;
        doc = (Document) load("staffNS", builder);
        newList = doc.getElementsByTagNameNS("*", "employee");
        assertEquals("employeeCount", 5, newList.getLength());
        newElement = (Element) newList.item(3);
        prefix = newElement.getPrefix();
        assertEquals("prefix", "emp", prefix);
        lname = newElement.getLocalName();
        assertEquals("lname", "employee", lname);
    }

    public void testGetElementsByTagNameNS3() throws Throwable {
        Document doc;
        NodeList elementList;
        Node child;
        String childName;
        List<String> result = new ArrayList<String>();

        List<String> expectedResult = new ArrayList<String>();
        expectedResult.add("employee");
        expectedResult.add("employeeId");
        expectedResult.add("name");
        expectedResult.add("position");
        expectedResult.add("salary");
        expectedResult.add("gender");
        expectedResult.add("address");
        expectedResult.add("emp:employee");
        expectedResult.add("emp:employeeId");
        expectedResult.add("emp:position");
        expectedResult.add("emp:salary");
        expectedResult.add("emp:gender");
        expectedResult.add("emp:address");
        expectedResult.add("address");

        doc = (Document) load("staffNS", builder);
        elementList = doc.getElementsByTagNameNS("http://www.nist.gov", "*");
        for (int indexN10076 = 0; indexN10076 < elementList.getLength(); indexN10076++) {
            child = (Node) elementList.item(indexN10076);
            childName = child.getNodeName();
            result.add(childName);
        }
        assertEquals("nodeNames", expectedResult, result);
    }

    public void testGetElementsByTagNameNS4() throws Throwable {
        Document doc;
        NodeList elementList;
        Node child;
        String childName;
        List<String> result = new ArrayList<String>();

        List<String> expectedResult = new ArrayList<String>();
        expectedResult.add("address");
        expectedResult.add("address");
        expectedResult.add("address");
        expectedResult.add("emp:address");
        expectedResult.add("address");

        doc = (Document) load("staffNS", builder);
        elementList = doc.getElementsByTagNameNS("*", "address");
        for (int indexN10059 = 0; indexN10059 < elementList.getLength(); indexN10059++) {
            child = (Node) elementList.item(indexN10059);
            childName = child.getNodeName();
            result.add(childName);
        }
        assertEquals("nodeNames", expectedResult, result);
    }

    public void testGetElementsByTagNameNS5() throws Throwable {
        String namespaceURI = "http://www.nist.gov";
        String localName = "nomatch";
        Document doc;
        NodeList elementList;
        doc = (Document) load("staffNS", builder);
        elementList = doc.getElementsByTagNameNS(namespaceURI, localName);
        assertEquals("throw_Size", 0, elementList.getLength());
    }

    public void testGetElementsByTagNameNS6() throws Throwable {
        Document doc;
        NodeList elementList;
        doc = (Document) load("staffNS", builder);
        elementList = doc.getElementsByTagNameNS("http://www.nomatch.com",
                "address");
        assertEquals("matchSize", 0, elementList.getLength());
    }

    public void testGetElementsByTagNameNS7() throws Throwable {
        Document doc;
        NodeList elementList;
        doc = (Document) load("staffNS", builder);
        elementList = doc.getElementsByTagNameNS("http://www.nist.gov",
                "address");
        assertEquals("addresses", 3, elementList.getLength());
    }

    public void testGetElementsByTagNameNS8() throws Throwable {
        Document doc;
        Element docElem;
        NodeList newList;
        doc = (Document) load("staffNS", builder);
        docElem = doc.getDocumentElement();
        newList = docElem.getElementsByTagNameNS("*", "*");
        assertEquals("listSize", 36, newList.getLength());
    }

    public void testGetElementsByTagNameNS9() throws Throwable {
        Document doc;
        NodeList newList;
        Element newElement;
        String prefix;
        String lname;
        Element docElem;
        doc = (Document) load("staffNS", builder);
        docElem = doc.getDocumentElement();
        newList = docElem.getElementsByTagNameNS("*", "employee");
        assertEquals("employeeCount", 5, newList.getLength());
        newElement = (Element) newList.item(3);
        prefix = newElement.getPrefix();
        assertEquals("prefix", "emp", prefix);
        lname = newElement.getLocalName();
        assertEquals("lname", "employee", lname);
    }

    public void testGetElementsByTagNameNS10() throws Throwable {
        Document doc;
        Element docElem;
        NodeList elementList;
        Node child;
        String childName;
        List<String> result = new ArrayList<String>();

        List<String> expectedResult = new ArrayList<String>();
        expectedResult.add("employee");
        expectedResult.add("employeeId");
        expectedResult.add("name");
        expectedResult.add("position");
        expectedResult.add("salary");
        expectedResult.add("gender");
        expectedResult.add("address");
        expectedResult.add("emp:employee");
        expectedResult.add("emp:employeeId");
        expectedResult.add("emp:position");
        expectedResult.add("emp:salary");
        expectedResult.add("emp:gender");
        expectedResult.add("emp:address");
        expectedResult.add("address");

        doc = (Document) load("staffNS", builder);
        docElem = doc.getDocumentElement();
        elementList = docElem
                .getElementsByTagNameNS("http://www.nist.gov", "*");
        for (int indexN1007E = 0; indexN1007E < elementList.getLength(); indexN1007E++) {
            child = (Node) elementList.item(indexN1007E);
            childName = child.getNodeName();
            result.add(childName);
        }
        assertEquals("nodeNames", expectedResult, result);
    }

    public void testGetElementsByTagNameNS11() throws Throwable {
        Document doc;
        Element docElem;
        NodeList elementList;
        Node child;
        String childName;
        List<String> result = new ArrayList<String>();

        List<String> expectedResult = new ArrayList<String>();
        expectedResult.add("address");
        expectedResult.add("address");
        expectedResult.add("address");
        expectedResult.add("emp:address");
        expectedResult.add("address");

        doc = (Document) load("staffNS", builder);
        docElem = doc.getDocumentElement();
        elementList = docElem.getElementsByTagNameNS("*", "address");
        for (int indexN1005E = 0; indexN1005E < elementList.getLength(); indexN1005E++) {
            child = (Node) elementList.item(indexN1005E);
            childName = child.getNodeName();
            result.add(childName);
        }
        assertEquals("nodeNames", expectedResult, result);
    }

    public void testGetElementsByTagNameNS12() throws Throwable {
        Document doc;
        Element docElem;
        NodeList elementList;
        doc = (Document) load("staffNS", builder);
        docElem = doc.getDocumentElement();
        elementList = docElem.getElementsByTagNameNS("http://www.nist.gov",
                "nomatch");
        assertEquals("size", 0, elementList.getLength());
    }

    public void testGetElementsByTagNameNS13() throws Throwable {
        Document doc;
        Element docElem;
        NodeList elementList;
        doc = (Document) load("staffNS", builder);
        docElem = doc.getDocumentElement();
        elementList = docElem.getElementsByTagNameNS("http://www.nomatch.com",
                "address");
        assertEquals("matchSize", 0, elementList.getLength());
    }

    public void testGetElementsByTagNameNS14() throws Throwable {
        Document doc;
        Element docElem;
        NodeList elementList;
        doc = (Document) load("staffNS", builder);
        docElem = doc.getDocumentElement();
        elementList = docElem.getElementsByTagNameNS("http://www.nist.gov",
                "address");
        assertEquals("addresses", 3, elementList.getLength());
    }
}
