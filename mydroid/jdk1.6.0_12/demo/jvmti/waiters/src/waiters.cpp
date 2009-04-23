/*
 * @(#)waiters.cpp	1.8 05/11/17
 * 
 * Copyright (c) 2006 Sun Microsystems, Inc. All Rights Reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * -Redistribution of source code must retain the above copyright notice, this
 *  list of conditions and the following disclaimer.
 * 
 * -Redistribution in binary form must reproduce the above copyright notice, 
 *  this list of conditions and the following disclaimer in the documentation
 *  and/or other materials provided with the distribution.
 * 
 * Neither the name of Sun Microsystems, Inc. or the names of contributors may 
 * be used to endorse or promote products derived from this software without 
 * specific prior written permission.
 * 
 * This software is provided "AS IS," without a warranty of any kind. ALL 
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. SUN MIDROSYSTEMS, INC. ("SUN")
 * AND ITS LICENSORS SHALL NOT BE LIABLE FOR ANY DAMAGES SUFFERED BY LICENSEE
 * AS A RESULT OF USING, MODIFYING OR DISTRIBUTING THIS SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL SUN OR ITS LICENSORS BE LIABLE FOR ANY LOST 
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL, 
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY 
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE THIS SOFTWARE, 
 * EVEN IF SUN HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 * 
 * You acknowledge that this software is not designed, licensed or intended
 * for use in the design, construction, operation or maintenance of any
 * nuclear facility.
 */

/* Example of using JVMTI events:
 *      JVMTI_EVENT_VM_INIT
 *      JVMTI_EVENT_VM_DEATH
 *      JVMTI_EVENT_THREAD_START
 *      JVMTI_EVENT_THREAD_END
 *      JVMTI_EVENT_MONITOR_CONTENDED_ENTER
 *      JVMTI_EVENT_MONITOR_WAIT
 *      JVMTI_EVENT_MONITOR_WAITED
 *      JVMTI_EVENT_OBJECT_FREE
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "jni.h"
#include "jvmti.h"

#include "agent_util.h"

#include "Monitor.hpp"
#include "Thread.hpp"
#include "Agent.hpp"

static jrawMonitorID vm_death_lock;
static jboolean      vm_death_active;

/* Given a jvmtiEnv*, return the C++ Agent class instance */
static Agent *
get_agent(jvmtiEnv *jvmti) 
{
    jvmtiError err;
    Agent     *agent;
    
    agent = NULL;
    err = jvmti->GetEnvironmentLocalStorage((void**)&agent);
    check_jvmti_error(jvmti, err, "get env local storage");
    if ( agent == NULL ) {
	/* This should never happen, but we should check */
	fatal_error("ERROR: GetEnvironmentLocalStorage() returned NULL");
    }
    return agent;
}

/* Enter raw monitor */
static void
menter(jvmtiEnv *jvmti, jrawMonitorID rmon)
{
    jvmtiError err;

    err = jvmti->RawMonitorEnter(rmon);
    check_jvmti_error(jvmti, err, "raw monitor enter");
}

/* Exit raw monitor */
static void
mexit(jvmtiEnv *jvmti, jrawMonitorID rmon)
{
    jvmtiError err;

    err = jvmti->RawMonitorExit(rmon);
    check_jvmti_error(jvmti, err, "raw monitor exit");
}


/* All callbacks need to be extern "C" */
extern "C" {
    static void JNICALL
    vm_init(jvmtiEnv *jvmti, JNIEnv *env, jthread thread) 
    {
        jvmtiError err;
        Agent     *agent;

	/* Create raw monitor to protect against threads running after death */
	err = jvmti->CreateRawMonitor("Waiters vm_death lock", &vm_death_lock);
	check_jvmti_error(jvmti, err, "create raw monitor");
	vm_death_active = JNI_FALSE;

	/* Create an Agent instance, set JVMTI Local Storage */
	agent = new Agent(jvmti, env, thread);
	err = jvmti->SetEnvironmentLocalStorage((const void*)agent);
        check_jvmti_error(jvmti, err, "set env local storage");

	/* Enable all other events we want */
	err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, 
			JVMTI_EVENT_VM_DEATH, NULL);
        check_jvmti_error(jvmti, err, "set event notify");
	err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, 
			JVMTI_EVENT_THREAD_START, NULL);
        check_jvmti_error(jvmti, err, "set event notify");
	err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, 
			JVMTI_EVENT_THREAD_END, NULL);
        check_jvmti_error(jvmti, err, "set event notify");
	err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, 
			JVMTI_EVENT_MONITOR_CONTENDED_ENTER, NULL);
        check_jvmti_error(jvmti, err, "set event notify");
	err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, 
			JVMTI_EVENT_MONITOR_CONTENDED_ENTERED, NULL);
        check_jvmti_error(jvmti, err, "set event notify");
	err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, 
			JVMTI_EVENT_MONITOR_WAIT, NULL);
        check_jvmti_error(jvmti, err, "set event notify");
	err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, 
			JVMTI_EVENT_MONITOR_WAITED, NULL);
        check_jvmti_error(jvmti, err, "set event notify");
	err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, 
			JVMTI_EVENT_OBJECT_FREE, NULL);
        check_jvmti_error(jvmti, err, "set event notify");
    }
    static void JNICALL
    vm_death(jvmtiEnv *jvmti, JNIEnv *env) 
    {
        jvmtiError err;
        Agent     *agent;

	/* Block all callbacks */
	menter(jvmti, vm_death_lock); {
	    /* Set flag for other callbacks */
	    vm_death_active = JNI_TRUE;

	    /* Inform Agent instance of VM_DEATH */
	    agent = get_agent(jvmti);
	    agent->vm_death(jvmti, env);

	    /* Reclaim space of Agent */
	    err = jvmti->SetEnvironmentLocalStorage((const void*)NULL);
            check_jvmti_error(jvmti, err, "set env local storage");
	    delete agent;
	} mexit(jvmti, vm_death_lock);

    }
    static void JNICALL
    thread_start(jvmtiEnv *jvmti, JNIEnv *env, jthread thread) 
    {
	menter(jvmti, vm_death_lock); {
	    if ( !vm_death_active ) {
		get_agent(jvmti)->thread_start(jvmti, env, thread);
	    }
	} mexit(jvmti, vm_death_lock);
    }
    static void JNICALL
    thread_end(jvmtiEnv *jvmti, JNIEnv *env, jthread thread) 
    {
	menter(jvmti, vm_death_lock); {
	    if ( !vm_death_active ) {
		get_agent(jvmti)->thread_end(jvmti, env, thread);
	    }
	} mexit(jvmti, vm_death_lock);
    }
    static void JNICALL
    monitor_contended_enter(jvmtiEnv* jvmti, JNIEnv *env, 
		 jthread thread, jobject object) 
    {
	menter(jvmti, vm_death_lock); {
	    if ( !vm_death_active ) {
		get_agent(jvmti)->monitor_contended_enter(jvmti, env, 
							  thread, object);
	    }
	} mexit(jvmti, vm_death_lock);
    }
    static void JNICALL
    monitor_contended_entered(jvmtiEnv* jvmti, JNIEnv *env,
		   jthread thread, jobject object) 
    {
	menter(jvmti, vm_death_lock); {
	    if ( !vm_death_active ) {
		get_agent(jvmti)->monitor_contended_entered(jvmti, env, 
							    thread, object);
	    }
	} mexit(jvmti, vm_death_lock);
    }
    static void JNICALL
    monitor_wait(jvmtiEnv* jvmti, JNIEnv *env, 
		 jthread thread, jobject object, jlong timeout) 
    {
	menter(jvmti, vm_death_lock); {
	    if ( !vm_death_active ) {
		get_agent(jvmti)->monitor_wait(jvmti, env, thread, 
					       object, timeout);
	    }
	} mexit(jvmti, vm_death_lock);
    }
    static void JNICALL
    monitor_waited(jvmtiEnv* jvmti, JNIEnv *env,
		   jthread thread, jobject object, jboolean timed_out) 
    {
	menter(jvmti, vm_death_lock); {
	    if ( !vm_death_active ) {
		get_agent(jvmti)->monitor_waited(jvmti, env, thread, 
					         object, timed_out);
	    }
	} mexit(jvmti, vm_death_lock);
    }
    static void JNICALL
    object_free(jvmtiEnv* jvmti, jlong tag) 
    {
	menter(jvmti, vm_death_lock); {
	    if ( !vm_death_active ) {
		get_agent(jvmti)->object_free(jvmti, tag);
	    }
	} mexit(jvmti, vm_death_lock);
    }

    /* Agent_OnLoad() is called first, we prepare for a VM_INIT event here. */
    JNIEXPORT jint JNICALL
    Agent_OnLoad(JavaVM *vm, char *options, void *reserved)
    {
	jvmtiEnv           *jvmti;
	jint                rc;
	jvmtiError          err;
	jvmtiCapabilities   capabilities;
	jvmtiEventCallbacks callbacks;
	
	/* Get JVMTI environment */
	rc = vm->GetEnv((void **)&jvmti, JVMTI_VERSION);
	if (rc != JNI_OK) {
	    fatal_error("ERROR: Unable to create jvmtiEnv, GetEnv failed, error=%d\n", rc);
	    return -1;
	}

	/* Get/Add JVMTI capabilities */
	(void)memset(&capabilities, 0, sizeof(capabilities));
	capabilities.can_generate_monitor_events 	= 1;
	capabilities.can_get_monitor_info 		= 1;
	capabilities.can_tag_objects 			= 1;
	capabilities.can_generate_object_free_events	= 1;
	err = jvmti->AddCapabilities(&capabilities);
        check_jvmti_error(jvmti, err, "add capabilities");

	/* Set all callbacks and enable VM_INIT event notification */
	memset(&callbacks, 0, sizeof(callbacks));
	callbacks.VMInit                  = &vm_init;
	callbacks.VMDeath                 = &vm_death;
	callbacks.ThreadStart             = &thread_start;
	callbacks.ThreadEnd               = &thread_end;
	callbacks.MonitorContendedEnter   = &monitor_contended_enter;
	callbacks.MonitorContendedEntered = &monitor_contended_entered;
	callbacks.MonitorWait             = &monitor_wait;
	callbacks.MonitorWaited           = &monitor_waited;
	callbacks.ObjectFree              = &object_free;
	err = jvmti->SetEventCallbacks(&callbacks, (jint)sizeof(callbacks));
        check_jvmti_error(jvmti, err, "set event callbacks");
	err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, 
			JVMTI_EVENT_VM_INIT, NULL);
        check_jvmti_error(jvmti, err, "set event notify");
	return 0;
    }

    /* Agent_OnUnload() is called last */
    JNIEXPORT void JNICALL
    Agent_OnUnload(JavaVM *vm)
    {
    }

} /* of extern "C" */

