package com.devflow.common.mybatis;
import java.sql.*;
import java.util.UUID;
import org.apache.ibatis.type.*;
@MappedTypes(UUID.class)
@MappedJdbcTypes(value=JdbcType.OTHER,includeNullJdbcType=true)
public class UuidTypeHandler extends BaseTypeHandler<UUID> {
 @Override public void setNonNullParameter(PreparedStatement s,int i,UUID value,JdbcType type)throws SQLException{s.setObject(i,value,Types.OTHER);}
 @Override public UUID getNullableResult(ResultSet r,String name)throws SQLException{return r.getObject(name,UUID.class);}
 @Override public UUID getNullableResult(ResultSet r,int i)throws SQLException{return r.getObject(i,UUID.class);}
 @Override public UUID getNullableResult(CallableStatement s,int i)throws SQLException{return s.getObject(i,UUID.class);}
}
