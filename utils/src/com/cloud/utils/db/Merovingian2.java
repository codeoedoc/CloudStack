/**
 *  Copyright (C) 2010 Cloud.com, Inc.  All rights reserved.
 * 
 * This software is licensed under the GNU General Public License v3 or later.
 * 
 * It is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 */
package com.cloud.utils.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import javax.management.StandardMBean;

import org.apache.log4j.Logger;

import com.cloud.utils.DateUtil;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.utils.mgmt.JmxUtil;
import com.cloud.utils.time.InaccurateClock;


public class Merovingian2 extends StandardMBean implements MerovingianMBean {
    private static final Logger s_logger = Logger.getLogger(Merovingian2.class);
    
    private static final String ACQUIRE_SQL = "INSERT INTO op_lock (op_lock.key, op_lock.mac, op_lock.ip, op_lock.thread, op_lock.acquired_on, waiters) VALUES (?, ?, ?, ?, ?, 1)";
    private static final String INCREMENT_SQL = "UPDATE op_lock SET waiters=waiters+1 where op_lock.key=? AND op_lock.mac=? AND op_lock.ip=? AND op_lock.thread=?";
    private static final String SELECT_SQL = "SELECT op_lock.key, mac, ip, thread, acquired_on, waiters FROM op_lock";
    private static final String INQUIRE_SQL = SELECT_SQL + " WHERE op_lock.key=?";
    private static final String DECREMENT_SQL = "UPDATE op_lock SET waiters=waiters-1 where op_lock.key=? AND op_lock.mac=? AND op_lock.ip=? AND op_lock.thread=?";
    private static final String RELEASE_LOCK_SQL = "DELETE FROM op_lock WHERE op_lock.key = ?";
    private static final String RELEASE_SQL = RELEASE_LOCK_SQL + " AND op_lock.mac=? AND waiters=0";
    private static final String CLEANUP_MGMT_LOCKS_SQL = "DELETE FROM op_lock WHERE op_lock.mac = ?";
    private static final String SELECT_MGMT_LOCKS_SQL = SELECT_SQL + " WHERE mac=?";
    private static final String SELECT_THREAD_LOCKS_SQL = SELECT_SQL + " WHERE mac=? AND ip=?";
    private static final String CLEANUP_THREAD_LOCKS_SQL = "DELETE FROM op_lock WHERE mac=? AND ip=? AND thread=?";
    
    TimeZone s_gmtTimeZone = TimeZone.getTimeZone("GMT");
    
    private long _msId;
    
    private static Merovingian2 s_instance = null;
    ConnectionConcierge _concierge = null;
    
    private Merovingian2(long msId) {
        super(MerovingianMBean.class, false);
        _msId = msId;
        Connection conn = null;
        try {
            conn = Transaction.getStandaloneConnectionWithException();
            _concierge = new ConnectionConcierge("LockMaster", conn, true, true);
        } catch (SQLException e) {
            s_logger.error("Unable to get a new db connection", e);
            throw new CloudRuntimeException("Unable to initialize a connection to the database for locking purposes: ", e);
        }
    }
    
    public static synchronized Merovingian2 createLockMaster(long msId) {
        assert s_instance == null : "No lock can serve two masters.  Either he will hate the one and love the other, or he will be devoted to the one and despise the other.";
        s_instance = new Merovingian2(msId);
        s_instance.cleanupThisServer();
        try {
            JmxUtil.registerMBean("Locks", "Locks", s_instance);
        } catch (Exception e) {
            s_logger.error("Unable to register for JMX", e);
        }
        return s_instance;
    }
    
    public static Merovingian2 getLockMaster() {
        return s_instance;
    }
    
    public boolean acquire(String key, int timeInSeconds) {
        Thread th = Thread.currentThread();
        String threadName = th.getName();
        int threadId = System.identityHashCode(th);
        
        if (s_logger.isTraceEnabled()) {
            s_logger.trace("Acquiring lck-" + key + " with wait time of " + timeInSeconds);
        }
        long startTime = InaccurateClock.getTime();

        while ((InaccurateClock.getTime() - startTime) < (timeInSeconds * 1000)) {
            int count = owns(key);
            if (count == -1) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                }
            } else if (count >= 1) {
                return increment(key, threadName, threadId);
            } else {
                if (doAcquire(key, threadName, threadId)) {
                    return true;
                }
            }
        }
        if (s_logger.isTraceEnabled()) {
            s_logger.trace("Timed out on acquiring lock " + key);
        }
        return false;
    }
    
    protected boolean increment(String key, String threadName, int threadId) {
        PreparedStatement pstmt = null;
        try {
            pstmt = _concierge.conn().prepareStatement(INCREMENT_SQL);
            pstmt.setString(1, key);
            pstmt.setLong(2, _msId);
            pstmt.setString(3, threadName);
            pstmt.setInt(4, threadId);
            int rows = pstmt.executeUpdate();
            assert (rows <= 1) : "hmm...non unique key? " + pstmt;
            if (s_logger.isTraceEnabled()) {
                s_logger.trace("lck-" + key + (rows == 1 ? " acquired for a second time" : " failed to acquire"));
            }
            return rows == 1;
        } catch (SQLException e) {
            throw new CloudRuntimeException("Unable to increment " + key, e);
        } finally {
            try {
                if (pstmt != null) {
                    pstmt.close();
                }
            } catch (SQLException e) {
            }
        }
    }
    
    protected boolean doAcquire(String key, String threadName, int threadId) {
        PreparedStatement pstmt = null;

        long startTime = InaccurateClock.getTime();
        try {
            pstmt = _concierge.conn().prepareStatement(ACQUIRE_SQL);
            pstmt.setString(1, key);
            pstmt.setLong(2, _msId);
            pstmt.setString(3, threadName);
            pstmt.setInt(4, threadId);
            pstmt.setString(5, DateUtil.getDateDisplayString(s_gmtTimeZone, new Date()));
            try {
                int rows = pstmt.executeUpdate();
                if (rows == 1) {
                    if (s_logger.isTraceEnabled()) {
                        s_logger.trace("Acquired for lck-" + key);
                    }
                    return true;
                }
            } catch(SQLException e) {
                if (!(e.getSQLState().equals("23000") && e.getErrorCode() == 1062)) {
                    throw new CloudRuntimeException("Unable to lock " + key + ".  Waited " + (InaccurateClock.getTime() - startTime), e);
                }
            }
        } catch(SQLException e) {
            throw new CloudRuntimeException("Unable to lock " + key + ".  Waited " + (InaccurateClock.getTime() - startTime), e);
        } finally {
            try {
                if (pstmt != null) {
                    pstmt.close();
                }
            } catch (SQLException e) {
            }
        }
            
        s_logger.trace("Unable to acquire lck-" + key);
        return false;
    }
    
    protected Map<String, String> isLocked(String key) {
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            pstmt = _concierge.conn().prepareStatement(INQUIRE_SQL);
            pstmt.setString(1, key);
            rs = pstmt.executeQuery();
            if (!rs.next()) {
                return null;
            }
            
            return toLock(rs);
        } catch (SQLException e) {
            throw new CloudRuntimeException("SQL Exception on inquiry", e);
        } finally {
            try {
                if (rs != null) {
                    rs.close();
                }
                if (pstmt != null) {
                    pstmt.close();
                }
            } catch (SQLException e) {
                s_logger.warn("Unexpected SQL exception " + e.getMessage(), e);
            }
        }
    }
    
    public void cleanupThisServer() {
        cleanupForServer(_msId);
    }

    @Override
    public void cleanupForServer(long msId) {
        PreparedStatement pstmt = null;
        try {
            pstmt = _concierge.conn().prepareStatement(CLEANUP_MGMT_LOCKS_SQL);
            pstmt.setLong(1, _msId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new CloudRuntimeException("Unable to clear the locks", e);
        } finally {
            try {
                if (pstmt != null) {
                    pstmt.close();
                }
            } catch (SQLException e) {
            }
        }
    }
    
    public boolean release(String key) {
        PreparedStatement pstmt = null;
        Thread th = Thread.currentThread();
        String threadName = th.getName();
        int threadId = System.identityHashCode(th);
        try {
            pstmt = _concierge.conn().prepareStatement(DECREMENT_SQL);
            pstmt.setString(1, key);
            pstmt.setLong(2, _msId);
            pstmt.setString(3, threadName);
            pstmt.setLong(4, threadId);
            int rows = pstmt.executeUpdate();
            assert (rows <= 1) : "hmmm....keys not unique? " + pstmt;
            if (s_logger.isTraceEnabled()) {
                s_logger.trace("lck-" + key + " released");
            }
            if (rows == 1) {
                pstmt.close();
                pstmt = _concierge.conn().prepareStatement(RELEASE_SQL);
                pstmt.setString(1, key);
                pstmt.setLong(2, _msId);
                int result = pstmt.executeUpdate();
                if (result == 1 && s_logger.isTraceEnabled()) {
                    s_logger.trace("lck-" + key + " removed");
                }
            }
            return rows == 1;
        } catch (SQLException e) {
            throw new CloudRuntimeException("Unable to release " + key, e);
        } finally {
            try {
                if (pstmt != null) {
                    pstmt.close();
                }
            } catch (SQLException e) {
            }
        }
    }
    
    protected Map<String, String> toLock(ResultSet rs) throws SQLException {
        Map<String, String> map = new HashMap<String, String>();
        map.put("key", rs.getString(1));
        map.put("mgmt", rs.getString(2));
        map.put("name", rs.getString(3));
        map.put("tid", Integer.toString(rs.getInt(4)));
        map.put("date", rs.getString(5));
        map.put("count", Integer.toString(rs.getInt(6)));
        return map;
        
    }
    
    protected List<Map<String, String>> toLocks(ResultSet rs) throws SQLException {
        LinkedList<Map<String, String>> results = new LinkedList<Map<String, String>>();
        while (rs.next()) {
            results.add(toLock(rs));
        }
        return results;
    }
    
    protected List<Map<String, String>> getLocks(String sql, Long msId) {
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            pstmt = _concierge.conn().prepareStatement(sql);
            if (msId != null) {
                pstmt.setLong(1, msId);
            }
            rs = pstmt.executeQuery();
            return toLocks(rs);
        } catch (SQLException e) {
            throw new CloudRuntimeException("Unable to retrieve locks ", e);
        } finally {
            try {
                if (rs != null) {
                    rs.close();
                }
                if (pstmt != null) {
                    pstmt.close();
                }
            } catch(SQLException e) {
            }
        }
    }

    @Override
    public List<Map<String, String>> getAllLocks() {
        return getLocks(SELECT_SQL, null);
    }

    @Override
    public List<Map<String, String>> getLocksAcquiredByThisServer() {
        return getLocks(SELECT_MGMT_LOCKS_SQL, _msId);
    }
    
    public int owns(String key) {
        Thread th = Thread.currentThread();
        int threadId = System.identityHashCode(th);
        Map<String, String> owner = isLocked(key);
        if (owner == null) {
            return 0;
        }
        if (owner.get("mgmt").equals(Long.toString(_msId)) && owner.get("tid").equals(Integer.toString(threadId))) {
            return Integer.parseInt(owner.get("count"));
        }
        return -1;
    }
    
    public List<Map<String, String>> getLocksAcquiredBy(long msId, String threadName) {
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            pstmt = _concierge.conn().prepareStatement(SELECT_THREAD_LOCKS_SQL);
            pstmt.setLong(1, msId);
            pstmt.setString(2, threadName);
            rs = pstmt.executeQuery();
            return toLocks(rs);
        } catch (SQLException e) {
            throw new CloudRuntimeException("Can't get locks " + pstmt, e);
        } finally {
            try {
                if (rs != null) {
                    rs.close();
                }
                if (pstmt != null) {
                    pstmt.close();
                }
            } catch (SQLException e) {
            }
        }
    }
    
    public void cleanupThread() {
        Thread th = Thread.currentThread();
        String threadName = th.getName();
        int threadId = System.identityHashCode(th);
                
        PreparedStatement pstmt = null;
        try {
            pstmt = _concierge.conn().prepareStatement(CLEANUP_THREAD_LOCKS_SQL);
            pstmt.setLong(1, _msId);
            pstmt.setString(2, threadName);
            pstmt.setInt(3, threadId);
            int rows = pstmt.executeUpdate();
            assert (rows == 0) : "Abandon hope, all ye who enter here....There were still " + rows + " locks not released when the transaction ended!";
        } catch (SQLException e) {
            throw new CloudRuntimeException("Can't clear locks " + pstmt, e);
        } finally {
            try {
                if (pstmt != null) {
                    pstmt.close();
                }
            } catch (SQLException e) {
            }
        }
    }
    
    @Override
    public boolean releaseLockAsLastResortAndIReallyKnowWhatIAmDoing(String key) {
        PreparedStatement pstmt = null;
        try {
            pstmt = _concierge.conn().prepareStatement(RELEASE_LOCK_SQL);
            pstmt.setString(1, key);
            int rows = pstmt.executeUpdate();
            return rows > 0;
        } catch (SQLException e) {
            s_logger.error("Unable to release lock " + key, e);
            return false;
        }
    }
}