/*
 * (c) Copyright 2006 Hewlett-Packard Development Company, LP
 * All rights reserved.
 * [See end of file]
 */

package sdb.test;

import java.io.UnsupportedEncodingException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class TestI18N extends TestDB 
{
    private Params params ;
    private String tempTableName ;
    private String testLabel ;
    private String baseString ; 
    
    static public final String kTempTableName     = "TempTable" ;
    static public final String kBinaryType        = "typeBinary" ;
    static public final String kBinaryCol         = "colBinary" ;

    static public final String kVarcharType    = "typeVarchar" ;
    static public final String kVarcharCol     = "colVarchar" ;

    //static Charset csUTF8 = Charset.forName("UTF-8") ;


    public TestI18N(String testLabel, String baseString, 
                    Connection jdbc, Params params, boolean verbose)
    {
        super(jdbc, verbose) ;
        this.params = params ;
        tempTableName = params.get(kTempTableName) ;
        this.testLabel = testLabel ;
        this.baseString = baseString ;
    }

    @Before
    public void before()
    {
        execNoFail("DROP TABLE %s", tempTableName) ;
    }
    
    @After
    public void after()
    { }
    
    // --------
    @Test
    public void text_ascii()
    { runTextTest(testLabel+"/Text", baseString, params.get(kVarcharCol), params.get(kVarcharType)) ; }

    @Test
    public void text_ascii_long()
    { runTextTest(testLabel+"Text/Long", longString(baseString, 198), params.get(kVarcharCol), params.get(kVarcharType)) ; }

    @Test
    public void binary_ascii()
    { runBytesTest(testLabel+"/Binary", baseString, params.get(kBinaryCol), params.get(kBinaryType)) ; }

    @Test
    public void binary_ascii_long()
    { runBytesTest(testLabel+"/Binary/Long", longString(baseString,1000), params.get(kBinaryCol), params.get(kBinaryType)) ; }
    
    private void runTextTest(String label, String testString, String colName, String colType)
    {
        testString = ":"+testString+":" ;
        try {
            exec("CREATE TABLE %s (%s %s)",  tempTableName, colName, colType) ;

            String $str = sqlFormat("INSERT INTO %s values (?)", tempTableName) ;
            if ( verbose )
                System.out.println($str) ;
            
            PreparedStatement ps = jdbc.prepareStatement($str) ;
            ps.setString(1, testString) ;
            ps.execute() ;
            ps.close() ;

            ResultSet rs = execQuery("SELECT %s FROM %s ", colName, tempTableName ) ;
            rs.next() ;
            // Null on empty strings (Oracle)
            String s = rs.getString(1) ;
            //if ( s == null ) s = "" ;
            rs.close() ;
            assertEquals(testLabel+" : "+label, testString, s) ;
            //System.out.println("Passed: "+label) ;
        } catch (SQLException ex)
        { fail("SQLException: "+ex.getMessage()) ; }
    }

    private void runBytesTest(String label, String testString, String colName, String colType)
    {
        testString = ":"+testString+":" ;
        try {
            exec("CREATE TABLE %s (%s %s)",  tempTableName, colName, colType) ;

            String $str = sqlFormat("INSERT INTO %s values (?)", tempTableName) ;
            if ( verbose )
                System.out.println($str) ;
            
            PreparedStatement ps = jdbc.prepareStatement($str) ;
            ps.setBytes(1, stringToBytes(testString)) ;
            ps.execute() ;
            ps.close() ;

            ResultSet rs = execQuery("SELECT %s FROM %s ", colName, tempTableName ) ;
            rs.next() ;
            byte[]b = rs.getBytes(1) ;

            // Null on empty strings (Oracle)
            String s = "" ;
//            if ( b != null )
            s = bytesToString(b) ;
            rs.close() ;
            assertEquals(testLabel+" : "+label, testString, s) ;
            //System.out.println("Passed: "+label) ;
        } catch (SQLException ex)
        { fail("SQLException: "+ex.getMessage()) ; }
    }
    
    private static String longString(String base,  int len)
    {
        if ( base.length() == 0 )
            return base ;
        
        StringBuilder value = new StringBuilder() ; 
        for ( int i = 0 ; i < len ; i++ )
        {
            value.append(base) ;
            if ( value.length() > len )
                break ;
        }
        // Trim.
        if ( value.length() > len )
            value = value.delete(len, value.length()) ;
        
        return value.toString() ;
    }
    
    // String(byte[], Charset) and .getBytes(Charset) are Java6-isms.
    
    String bytesToString(byte[] b)
    {
        if ( b == null )
            fail(testLabel+": bytesToString(null)") ;
        
        try { return new String(b, "UTF-8") ; }
        catch (UnsupportedEncodingException ex)
        {
            ex.printStackTrace();
            throw new RuntimeException("No UTF-8 - should not happen") ;
        }
    }
    byte[] stringToBytes(String s)
    {
        if ( s == null )
            fail(testLabel+": stringToByte(null)") ;
        try { return s.getBytes("UTF-8") ; } 
        catch (UnsupportedEncodingException ex)
        {
            ex.printStackTrace();
            throw new RuntimeException("No UTF-8 - should not happen") ;
        }
        
    }
}

/*
 * (c) Copyright 2006 Hewlett-Packard Development Company, LP
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. The name of the author may not be used to endorse or promote products
 *    derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 * THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */