/*
 * aTalk, android VoIP and Instant Messaging client
 * Copyright 2014 Eng Chong Meng
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.atalk.persistance.migrations;

import android.content.Context;

import net.java.sip.communicator.service.protocol.AccountID;

// import com.google.android.gms.phenotype.Flag;

/**
 * Helper to allow accessing classes and methods that aren't visible or accessible to the
 * 'migrations' package
 */
public interface MigrationsHelper
{
	AccountID getAccountId();
	Context getContext();
	// String serializeFlags(List<Flag> flags);
}
