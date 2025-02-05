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

package org.apache.hadoop.fs.azurebfs.constants;

import org.apache.hadoop.classification.InterfaceAudience;
import org.apache.hadoop.classification.InterfaceStability;

/**
 * Responsible to keep all Azure Blob File System valid URI schemes.
 */
@InterfaceAudience.Public
@InterfaceStability.Evolving
public final class FileSystemUriSchemes {
  public static final String ABFS_SCHEME = "abfs";
  public static final String ABFS_SECURE_SCHEME = "abfss";
  public static final String ABFS_DNS_PREFIX = "dfs";

  public static final String HTTP_SCHEME = "http";
  public static final String HTTPS_SCHEME = "https";

  public static final String WASB_SCHEME = "wasb";
  public static final String WASB_SECURE_SCHEME = "wasbs";
  public static final String WASB_DNS_PREFIX = "blob";

  public static final String ABFS_DFS_DOMAIN_NAME = ".dfs.";
  public static final String ABFS_BLOB_DOMAIN_NAME = ".blob.";

  private FileSystemUriSchemes() {}
}
