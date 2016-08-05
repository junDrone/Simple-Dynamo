package edu.buffalo.cse.cse486586.simpledynamo;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Created by arjunsun on 4/13/16.
 */
public class Forsql extends SQLiteOpenHelper {

    public static final String tablename = "mytable";
    public static final String col1 = "key";
    public static final String col2 = "value";

    private static final String DATABASE_NAME = "dhtdb.db";
    private static final int DATABASE_VERSION = 1;


    private static final String tablecreate = "CREATE TABLE "+ tablename + "("  + col1 + " varchar primary key,"
            +col2+ " varchar);";
    private static final String qurr="DROP TABLE IF EXISTS "+tablename;
    private static final String qurr82="ALTER TABLE IF EXISTS mytable";
    public Forsql(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase database) {
        database.execSQL(qurr);
        database.execSQL(tablecreate);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {


    }

}
