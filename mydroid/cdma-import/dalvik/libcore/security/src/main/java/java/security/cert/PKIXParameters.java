/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

/**
* @author Vladimir N. Molotkov
* @version $Revision$
*/

package java.security.cert;

import java.security.InvalidAlgorithmParameterException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.harmony.security.internal.nls.Messages;

/**
 * @com.intel.drl.spec_ref
 * 
 */
public class PKIXParameters implements CertPathParameters {
    // Set of trust anchors - most trusted CAs
    private Set<TrustAnchor> trustAnchors;
    // Set of acceptable initial policy identifiers (OID strings)
    private Set<String> initialPolicies;
    // List of cert stores that used to find certificates and CRLs
    private List<CertStore> certStores;
    // Time for which the validity of the certification
    // patch should be determined
    private Date date;
    // List of certification patch checkers (PKIXCertPathChecker)
    private List<PKIXCertPathChecker> certPathCheckers;
    // Preferred signature provider name
    private String sigProvider;
    // Required constraints on the target certificate
    private CertSelector targetCertConstraints;
    // Indicates whether cert revocation is enabled or not
    private boolean revocationEnabled = true;
    // Indicates whether explicit policy required or not
    private boolean explicitPolicyRequired = false;
    // Indicates whether policy mapping inhibited or not
    private boolean policyMappingInhibited = false;
    // Indicates whether any policy inhibited or not
    private boolean anyPolicyInhibited = false;
    // Indicates whether certificates that include policy
    // qualifiers in a certificate policies extension that
    // is marked critical must be rejected or not
    private boolean policyQualifiersRejected = true;

    /**
     * @com.intel.drl.spec_ref
     */
    public PKIXParameters(Set<TrustAnchor> trustAnchors)
        throws InvalidAlgorithmParameterException {
        if (trustAnchors == null) {
            throw new NullPointerException(Messages.getString("security.6F")); //$NON-NLS-1$
        }
        checkTrustAnchors(trustAnchors);
        this.trustAnchors = new HashSet<TrustAnchor>(trustAnchors);
    }

    /**
     * @com.intel.drl.spec_ref
     */
    public PKIXParameters(KeyStore keyStore)
        throws KeyStoreException,
               InvalidAlgorithmParameterException {
        if (keyStore == null) {
            throw new NullPointerException(Messages.getString("security.41")); //$NON-NLS-1$
        }
        // Will throw KeyStoreException if
        // keyStore has not been initialized (loaded)
        if (keyStore.size() == 0) {
            throw new InvalidAlgorithmParameterException(
                    Messages.getString("security.6A")); //$NON-NLS-1$
        }
        // keyStore is not null and loaded
        trustAnchors = new HashSet<TrustAnchor>();
        for (Enumeration i = keyStore.aliases(); i.hasMoreElements();) {
            String alias = (String) i.nextElement();
            if (keyStore.isCertificateEntry(alias)) {
                // this is trusted certificate entry
                // check if it is X509Cerificate
                Certificate c = keyStore.getCertificate(alias);
                // add only X509Cerificate
                // ignore all other types
                if (c instanceof X509Certificate) {
                    trustAnchors.add(new TrustAnchor((X509Certificate)c, null));
                }
            }
        }
        checkTrustAnchors(trustAnchors);
    }

    /**
     * @com.intel.drl.spec_ref
     */
    public Set<TrustAnchor> getTrustAnchors() {
        return Collections.unmodifiableSet(trustAnchors);
    }

    /**
     * @com.intel.drl.spec_ref
     */
    public void setTrustAnchors(Set<TrustAnchor> trustAnchors)
        throws InvalidAlgorithmParameterException {
        if (trustAnchors == null) {
            throw new NullPointerException(
                    Messages.getString("security.6F")); //$NON-NLS-1$
        }
        checkTrustAnchors(trustAnchors);
        // make shallow copy
        this.trustAnchors = new HashSet<TrustAnchor>(trustAnchors);
    }

    /**
     * @com.intel.drl.spec_ref
     */
    public boolean isAnyPolicyInhibited() {
        return anyPolicyInhibited;
    }

    /**
     * @com.intel.drl.spec_ref
     */
    public void setAnyPolicyInhibited(boolean anyPolicyInhibited) {
        this.anyPolicyInhibited = anyPolicyInhibited;
    }

    /**
     * @com.intel.drl.spec_ref
     */
    public List<PKIXCertPathChecker> getCertPathCheckers() {
        if (certPathCheckers == null) {
            // set to empty List if has not been set yet
            certPathCheckers = new ArrayList<PKIXCertPathChecker>();
        }
        if (certPathCheckers.isEmpty()) {
            // no content - no need to copy,
            // just return immutable view of the same
            // empty List each time
            return Collections.unmodifiableList(certPathCheckers);
        }
        // List is not empty - do deep copy
        ArrayList<PKIXCertPathChecker> modifiableList = 
            new ArrayList<PKIXCertPathChecker>();
        for (Iterator<PKIXCertPathChecker> i 
                = certPathCheckers.iterator(); i.hasNext();) {
            modifiableList.add((PKIXCertPathChecker)i.next().clone());
        }
        return Collections.unmodifiableList(modifiableList);
    }

    /**
     * @com.intel.drl.spec_ref
     */
    public void setCertPathCheckers(List<PKIXCertPathChecker> certPathCheckers) {
        if (certPathCheckers == null || certPathCheckers.isEmpty()) {
            // empty list or null provided
            if (this.certPathCheckers != null &&
               !this.certPathCheckers.isEmpty()) {
                // discard non-empty list
                this.certPathCheckers = null;
            }
            return;
        }
        // non-empty list provided - do deep copy
        this.certPathCheckers = new ArrayList<PKIXCertPathChecker>();
        for (Iterator<PKIXCertPathChecker> i 
                = certPathCheckers.iterator(); i.hasNext();) {
            this.certPathCheckers.add((PKIXCertPathChecker)i.next().clone());
        }
    }

    /**
     * @com.intel.drl.spec_ref
     */
    public void addCertPathChecker(PKIXCertPathChecker checker) {
        if (checker == null) {
            // do nothing if null provided
            return;
        }
        if (certPathCheckers == null) {
            // set to empty List if has not been set yet
            certPathCheckers = new ArrayList<PKIXCertPathChecker>();
        }
        // add a copy to avoid possible modifications
        certPathCheckers.add((PKIXCertPathChecker) checker.clone());
    }

    /**
     * @com.intel.drl.spec_ref
     */
    public List<CertStore> getCertStores() {
        if (certStores == null) {
            // set to empty List if has not been set yet
            certStores = new ArrayList<CertStore>();
        }
        if (certStores.isEmpty()) {
            // no content - no need to copy,
            // just return immutable view of the same
            // empty List each time
            return Collections.unmodifiableList(certStores);
        }
        // List is not empty - do shallow copy
        ArrayList<CertStore> modifiableList 
            = new ArrayList<CertStore>(certStores);
        return Collections.unmodifiableList(modifiableList);
    }

    /**
     * @com.intel.drl.spec_ref
     */
    public void setCertStores(List<CertStore> certStores) {
        if (certStores == null || certStores.isEmpty()) {
            // empty list or null provided
            if (this.certStores != null && !this.certStores.isEmpty()) {
                // discard non-empty list
                this.certStores = null;
            }
            return;
        }
        // non-empty list provided - do shallow copy
        this.certStores = new ArrayList(certStores);
        // check that all elements are CertStore
        for (Iterator i = this.certStores.iterator(); i.hasNext();) {
            if (!(i.next() instanceof CertStore)) {
                throw new ClassCastException(Messages.getString("security.6B")); //$NON-NLS-1$
            }
        }
    }

    /**
     * @com.intel.drl.spec_ref
     */
    public void addCertStore(CertStore store) {
        if (store == null) {
            // do nothing if null provided
            return;
        }
        if (certStores == null) {
            // set to empty List if has not been set yet
            certStores = new ArrayList();
        }
        // add store
        certStores.add(store);
    }

    /**
     * @com.intel.drl.spec_ref
     */
    public Date getDate() {
        return date == null ? null : (Date)date.clone();
    }

    /**
     * @com.intel.drl.spec_ref
     */
    public void setDate(Date date) {
        this.date = (date == null ? null : new Date(date.getTime()));
    }

    /**
     * @com.intel.drl.spec_ref
     */
    public boolean isExplicitPolicyRequired() {
        return explicitPolicyRequired;
    }

    /**
     * @com.intel.drl.spec_ref
     */
    public void setExplicitPolicyRequired(boolean explicitPolicyRequired) {
        this.explicitPolicyRequired = explicitPolicyRequired;
    }

    /**
     * @com.intel.drl.spec_ref
     */
    public Set<String> getInitialPolicies() {
        if (initialPolicies == null) {
            // set to empty Set if has not been set yet
            initialPolicies = new HashSet();
        }
        if (initialPolicies.isEmpty()) {
            // no content - no need to copy,
            // just return immutable view of the same
            // empty Set each time
            return Collections.unmodifiableSet(initialPolicies);
        }
        // List is not empty - do shallow copy
        HashSet modifiableSet = new HashSet(initialPolicies);
        return Collections.unmodifiableSet(modifiableSet);
    }

    /**
     * @com.intel.drl.spec_ref
     */
    public void setInitialPolicies(Set<String> initialPolicies) {
        if (initialPolicies == null || initialPolicies.isEmpty()) {
            // empty list or null provided
            if (this.initialPolicies != null &&
               !this.initialPolicies.isEmpty()) {
                // discard non-empty list
                this.initialPolicies = null;
            }
            return;
        }
        // non-empty list provided - do shallow copy
        this.initialPolicies = new HashSet(initialPolicies);
        // check that all elements are String
        for (Iterator i = this.initialPolicies.iterator(); i.hasNext();) {
            if (!(i.next() instanceof String)) {
                throw new ClassCastException(Messages.getString("security.6C")); //$NON-NLS-1$
            }
        }
    }

    /**
     * @com.intel.drl.spec_ref
     */
    public boolean isPolicyMappingInhibited() {
        return policyMappingInhibited;
    }

    /**
     * @com.intel.drl.spec_ref
     */
    public void setPolicyMappingInhibited(boolean policyMappingInhibited) {
        this.policyMappingInhibited = policyMappingInhibited;
    }

    /**
     * @com.intel.drl.spec_ref
     */
    public boolean getPolicyQualifiersRejected() {
        return policyQualifiersRejected;
    }

    /**
     * @com.intel.drl.spec_ref
     */
    public void setPolicyQualifiersRejected(boolean policyQualifiersRejected) {
        this.policyQualifiersRejected = policyQualifiersRejected;
    }

    /**
     * @com.intel.drl.spec_ref
     */
    public boolean isRevocationEnabled() {
        return revocationEnabled;
    }

    /**
     * @com.intel.drl.spec_ref
     */
    public void setRevocationEnabled(boolean revocationEnabled) {
        this.revocationEnabled = revocationEnabled;
    }

    /**
     * @com.intel.drl.spec_ref
     */
    public String getSigProvider() {
        return sigProvider;
    }

    /**
     * @com.intel.drl.spec_ref
     */
    public void setSigProvider(String sigProvider) {
        this.sigProvider = sigProvider;
    }

    /**
     * @com.intel.drl.spec_ref
     */
    public CertSelector getTargetCertConstraints() {
        return (targetCertConstraints == null ? null
                :(CertSelector)targetCertConstraints.clone());
    }

    /**
     * @com.intel.drl.spec_ref
     */
    public void setTargetCertConstraints(CertSelector targetCertConstraints) {
        this.targetCertConstraints = (targetCertConstraints == null ? null
                : (CertSelector)targetCertConstraints.clone());
    }

    /**
     * @com.intel.drl.spec_ref
     */
    public Object clone() {
        try {
            // do shallow copy first
            PKIXParameters ret = (PKIXParameters)super.clone();
            // copy fields containing references to mutable objects
            if (this.certStores != null) {
                ret.certStores = new ArrayList(this.certStores);
            }
            if (this.certPathCheckers != null) {
                ret.certPathCheckers = new ArrayList(this.certPathCheckers);
            }
            return ret;
        } catch (CloneNotSupportedException e) {
            throw new Error(e);
        }
    }

    /**
     * @com.intel.drl.spec_ref
     */
    public String toString() {
        StringBuffer sb =
            new StringBuffer("[\n Trust Anchors: "); //$NON-NLS-1$
        sb.append(trustAnchors);
        sb.append("\n Revocation Enabled: "); //$NON-NLS-1$
        sb.append(revocationEnabled);
        sb.append("\n Explicit Policy Required: "); //$NON-NLS-1$
        sb.append(explicitPolicyRequired);
        sb.append("\n Policy Mapping Inhibited: "); //$NON-NLS-1$
        sb.append(policyMappingInhibited);
        sb.append("\n Any Policy Inhibited: "); //$NON-NLS-1$
        sb.append(anyPolicyInhibited);
        sb.append("\n Policy Qualifiers Rejected: "); //$NON-NLS-1$
        sb.append(policyQualifiersRejected);
        sb.append("\n Initial Policy OIDs: "); //$NON-NLS-1$
        sb.append((initialPolicies == null || initialPolicies.isEmpty())
                ? "any" : initialPolicies.toString()); //$NON-NLS-1$
        sb.append("\n Cert Stores: "); //$NON-NLS-1$
        sb.append((certStores==null||certStores.isEmpty())?
                "no":certStores.toString()); //$NON-NLS-1$
        sb.append("\n Validity Date: "); //$NON-NLS-1$
        sb.append(date);
        sb.append("\n Cert Path Checkers: "); //$NON-NLS-1$
        sb.append((certPathCheckers==null||certPathCheckers.isEmpty())?
                "no":certPathCheckers.toString()); //$NON-NLS-1$
        sb.append("\n Signature Provider: "); //$NON-NLS-1$
        sb.append(sigProvider);
        sb.append("\n Target Certificate Constraints: "); //$NON-NLS-1$
        sb.append(targetCertConstraints);
        sb.append("\n]"); //$NON-NLS-1$
        return sb.toString();
    }

    //
    // Private stuff
    //

    //
    // Checks that 'trustAnchors' contains
    // only TrustAnchor instances.
    // Throws InvalidAlgorithmParameterException if trustAnchors set is empty.
    //
    private void checkTrustAnchors(Set trustAnchors)
        throws InvalidAlgorithmParameterException {
        if (trustAnchors.isEmpty()) {
            throw new InvalidAlgorithmParameterException(
                    Messages.getString("security.6D")); //$NON-NLS-1$
        }
        for (Iterator i = trustAnchors.iterator(); i.hasNext();) {
            if (!(i.next() instanceof TrustAnchor)) {
                throw new ClassCastException(
             Messages.getString("security.6E")); //$NON-NLS-1$
            }
        }
    }
}
