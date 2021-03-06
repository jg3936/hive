/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.hive.ql.security.authorization.plugin.sqlstd;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.hadoop.hive.metastore.IMetaStoreClient;
import org.apache.hadoop.hive.metastore.api.HiveObjectPrivilege;
import org.apache.hadoop.hive.metastore.api.HiveObjectRef;
import org.apache.hadoop.hive.metastore.api.HiveObjectType;
import org.apache.hadoop.hive.metastore.api.MetaException;
import org.apache.hadoop.hive.metastore.api.NoSuchObjectException;
import org.apache.hadoop.hive.metastore.api.PrincipalPrivilegeSet;
import org.apache.hadoop.hive.metastore.api.PrivilegeBag;
import org.apache.hadoop.hive.metastore.api.PrivilegeGrantInfo;
import org.apache.hadoop.hive.metastore.api.Table;
import org.apache.hadoop.hive.ql.metadata.HiveException;
import org.apache.hadoop.hive.ql.security.authorization.AuthorizationUtils;
import org.apache.hadoop.hive.ql.security.authorization.plugin.HiveAccessControlException;
import org.apache.hadoop.hive.ql.security.authorization.plugin.HiveAuthzPluginException;
import org.apache.hadoop.hive.ql.security.authorization.plugin.HivePrincipal;
import org.apache.hadoop.hive.ql.security.authorization.plugin.HivePrivilege;
import org.apache.hadoop.hive.ql.security.authorization.plugin.HivePrivilegeObject;
import org.apache.hadoop.hive.ql.security.authorization.plugin.HivePrivilegeObject.HivePrivilegeObjectType;
import org.apache.hadoop.hive.ql.security.authorization.plugin.HiveRole;
import org.apache.thrift.TException;

public class SQLAuthorizationUtils {

  private static final String[] SUPPORTED_PRIVS = { "INSERT", "UPDATE", "DELETE", "SELECT" };
  private static final Set<String> SUPPORTED_PRIVS_SET = new HashSet<String>(
      Arrays.asList(SUPPORTED_PRIVS));

  /**
   * Create thrift privileges bag
   *
   * @param hivePrincipals
   * @param hivePrivileges
   * @param hivePrivObject
   * @param grantorPrincipal
   * @param grantOption
   * @return
   * @throws HiveAuthzPluginException
   */
  static PrivilegeBag getThriftPrivilegesBag(List<HivePrincipal> hivePrincipals,
      List<HivePrivilege> hivePrivileges, HivePrivilegeObject hivePrivObject,
      HivePrincipal grantorPrincipal, boolean grantOption) throws HiveAuthzPluginException {
    HiveObjectRef privObj = getThriftHiveObjectRef(hivePrivObject);
    PrivilegeBag privBag = new PrivilegeBag();
    for (HivePrivilege privilege : hivePrivileges) {
      if (privilege.getColumns() != null && privilege.getColumns().size() > 0) {
        throw new HiveAuthzPluginException("Privileges on columns not supported currently"
            + " in sql standard authorization mode");
      }
      if (!SUPPORTED_PRIVS_SET.contains(privilege.getName().toUpperCase(Locale.US))) {
        throw new HiveAuthzPluginException("Privilege: " + privilege.getName()
            + " is not supported in sql standard authorization mode");
      }
      PrivilegeGrantInfo grantInfo = getThriftPrivilegeGrantInfo(privilege, grantorPrincipal,
          grantOption);
      for (HivePrincipal principal : hivePrincipals) {
        HiveObjectPrivilege objPriv = new HiveObjectPrivilege(privObj, principal.getName(),
            AuthorizationUtils.getThriftPrincipalType(principal.getType()), grantInfo);
        privBag.addToPrivileges(objPriv);
      }
    }
    return privBag;
  }

  static PrivilegeGrantInfo getThriftPrivilegeGrantInfo(HivePrivilege privilege,
      HivePrincipal grantorPrincipal, boolean grantOption) throws HiveAuthzPluginException {
    try {
      return AuthorizationUtils.getThriftPrivilegeGrantInfo(privilege, grantorPrincipal,
          grantOption);
    } catch (HiveException e) {
      throw new HiveAuthzPluginException(e);
    }
  }

  /**
   * Create a thrift privilege object from the plugin interface privilege object
   *
   * @param privObj
   * @return
   * @throws HiveAuthzPluginException
   */
  static HiveObjectRef getThriftHiveObjectRef(HivePrivilegeObject privObj)
      throws HiveAuthzPluginException {
    try {
      return AuthorizationUtils.getThriftHiveObjectRef(privObj);
    } catch (HiveException e) {
      throw new HiveAuthzPluginException(e);
    }
  }

  static HivePrivilegeObjectType getPluginObjType(HiveObjectType objectType)
      throws HiveAuthzPluginException {
    switch (objectType) {
    case DATABASE:
      return HivePrivilegeObjectType.DATABASE;
    case TABLE:
      return HivePrivilegeObjectType.TABLE_OR_VIEW;
    case COLUMN:
    case GLOBAL:
    case PARTITION:
      throw new HiveAuthzPluginException("Unsupported object type " + objectType);
    default:
      throw new AssertionError("Unexpected object type " + objectType);
    }
  }

  /**
   * Check if the privileges are acceptable for SQL Standard authorization implementation
   * @param hivePrivileges
   * @throws HiveAuthzPluginException
   */
  public static void validatePrivileges(List<HivePrivilege> hivePrivileges) throws HiveAuthzPluginException {
    for (HivePrivilege hivePrivilege : hivePrivileges) {
      if (hivePrivilege.getColumns() != null && hivePrivilege.getColumns().size() != 0) {
        throw new HiveAuthzPluginException(
            "Privilege with columns are not currently supported with sql standard authorization:"
                + hivePrivilege);
      }
      //try converting to the enum to verify that this is a valid privilege type
      SQLPrivilegeType.getRequirePrivilege(hivePrivilege.getName());

    }
  }

  /**
   * Get the privileges this user(userName argument) has on the object
   * (hivePrivObject argument) If isAdmin is true, adds an admin privilege as
   * well.
   *
   * @param metastoreClient
   * @param userName
   * @param hivePrivObject
   * @param curRoles
   *          current active roles for user
   * @param isAdmin
   *          if user can run as admin user
   * @return
   * @throws HiveAuthzPluginException
   */
  static RequiredPrivileges getPrivilegesFromMetaStore(IMetaStoreClient metastoreClient,
      String userName, HivePrivilegeObject hivePrivObject, List<HiveRole> curRoles, boolean isAdmin)
          throws HiveAuthzPluginException {

    // get privileges for this user and its role on this object
    PrincipalPrivilegeSet thrifPrivs = null;
    try {
      thrifPrivs = metastoreClient.get_privilege_set(
          AuthorizationUtils.getThriftHiveObjectRef(hivePrivObject), userName, null);
    } catch (MetaException e) {
      throwGetPrivErr(e, hivePrivObject, userName);
    } catch (TException e) {
      throwGetPrivErr(e, hivePrivObject, userName);
    } catch (HiveException e) {
      throwGetPrivErr(e, hivePrivObject, userName);
    }

    filterPrivsByCurrentRoles(thrifPrivs, curRoles);

    // convert to RequiredPrivileges
    RequiredPrivileges privs = getRequiredPrivsFromThrift(thrifPrivs);

    // add owner privilege if user is owner of the object
    if (isOwner(metastoreClient, userName, hivePrivObject)) {
      privs.addPrivilege(SQLPrivTypeGrant.OWNER_PRIV);
    }
    if (isAdmin) {
      privs.addPrivilege(SQLPrivTypeGrant.ADMIN_PRIV);
    }

    return privs;
  }

  /**
   * Remove any role privileges that don't belong to the roles in curRoles
   * @param thriftPrivs
   * @param curRoles
   * @return
   */
  private static void filterPrivsByCurrentRoles(PrincipalPrivilegeSet thriftPrivs,
      List<HiveRole> curRoles) {
    // check if there are privileges to be filtered
    if(thriftPrivs == null || thriftPrivs.getRolePrivileges() == null
        || thriftPrivs.getRolePrivilegesSize() == 0
        ){
      // no privileges to filter
      return;
    }

    // add the privs for roles in curRoles to new role-to-priv map
    Map<String, List<PrivilegeGrantInfo>> filteredRolePrivs = new HashMap<String, List<PrivilegeGrantInfo>>();
    for(HiveRole role : curRoles){
      String roleName = role.getRoleName();
      List<PrivilegeGrantInfo> privs = thriftPrivs.getRolePrivileges().get(roleName);
      if(privs != null){
        filteredRolePrivs.put(roleName, privs);
      }
    }
    thriftPrivs.setRolePrivileges(filteredRolePrivs);
  }

  /**
   * Check if user is owner of the given object
   *
   * @param metastoreClient
   * @param userName
   *          user
   * @param hivePrivObject
   *          given object
   * @return true if user is owner
   * @throws HiveAuthzPluginException
   */
  private static boolean isOwner(IMetaStoreClient metastoreClient, String userName,
      HivePrivilegeObject hivePrivObject) throws HiveAuthzPluginException {
    //for now, check only table
    if(hivePrivObject.getType() == HivePrivilegeObjectType.TABLE_OR_VIEW){
      Table thriftTableObj = null;
      try {
        thriftTableObj = metastoreClient.getTable(hivePrivObject.getDbname(), hivePrivObject.getTableViewURI());
      } catch (MetaException e) {
        throwGetTableErr(e, hivePrivObject);
      } catch (NoSuchObjectException e) {
        throwGetTableErr(e, hivePrivObject);
      } catch (TException e) {
        throwGetTableErr(e, hivePrivObject);
      }
      return userName.equals(thriftTableObj.getOwner());
    }
    return false;
  }

  private static void throwGetTableErr(Exception e, HivePrivilegeObject hivePrivObject)
      throws HiveAuthzPluginException {
    String msg = "Error getting table object from metastore for" + hivePrivObject;
    throw new HiveAuthzPluginException(msg, e);
  }

  private static void throwGetPrivErr(Exception e, HivePrivilegeObject hivePrivObject,
      String userName) throws HiveAuthzPluginException {
    String msg = "Error getting privileges on " + hivePrivObject + " for " + userName + ": "
      + e.getMessage();
    throw new HiveAuthzPluginException(msg, e);
  }

  private static RequiredPrivileges getRequiredPrivsFromThrift(PrincipalPrivilegeSet thrifPrivs)
      throws HiveAuthzPluginException {

    RequiredPrivileges reqPrivs = new RequiredPrivileges();
    // add user privileges
    Map<String, List<PrivilegeGrantInfo>> userPrivs = thrifPrivs.getUserPrivileges();
    if (userPrivs != null && userPrivs.size() != 1) {
      throw new HiveAuthzPluginException("Invalid number of user privilege objects: "
          + userPrivs.size());
    }
    addRequiredPrivs(reqPrivs, userPrivs);

    // add role privileges
    Map<String, List<PrivilegeGrantInfo>> rolePrivs = thrifPrivs.getRolePrivileges();
    addRequiredPrivs(reqPrivs, rolePrivs);
    return reqPrivs;
  }

  /**
   * Add privileges to RequiredPrivileges object reqPrivs from thrift availPrivs
   * object
   * @param reqPrivs
   * @param availPrivs
   * @throws HiveAuthzPluginException
   */
  private static void addRequiredPrivs(RequiredPrivileges reqPrivs,
      Map<String, List<PrivilegeGrantInfo>> availPrivs) throws HiveAuthzPluginException {
    if(availPrivs == null){
      return;
    }
    for (Map.Entry<String, List<PrivilegeGrantInfo>> userPriv : availPrivs.entrySet()) {
      List<PrivilegeGrantInfo> userPrivGInfos = userPriv.getValue();
      for (PrivilegeGrantInfo userPrivGInfo : userPrivGInfos) {
        reqPrivs.addPrivilege(userPrivGInfo.getPrivilege(), userPrivGInfo.isGrantOption());
      }
    }
  }

  public static void assertNoMissingPrivilege(Collection<SQLPrivTypeGrant> missingPrivs,
      HivePrincipal hivePrincipal, HivePrivilegeObject hivePrivObject)
      throws HiveAccessControlException {
    if (missingPrivs.size() != 0) {
      // there are some required privileges missing, create error message
      // sort the privileges so that error message is deterministic (for tests)
      List<SQLPrivTypeGrant> sortedmissingPrivs = new ArrayList<SQLPrivTypeGrant>(missingPrivs);
      Collections.sort(sortedmissingPrivs);

      String errMsg = "Permission denied. " + hivePrincipal
          + " does not have following privileges on " + hivePrivObject + " : " + sortedmissingPrivs;
      throw new HiveAccessControlException(errMsg.toString());
    }
  }


}
