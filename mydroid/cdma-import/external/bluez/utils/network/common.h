/*
 *
 *  BlueZ - Bluetooth protocol stack for Linux
 *
 *  Copyright (C) 2004-2008  Marcel Holtmann <marcel@holtmann.org>
 *
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 */

#define MAX_PATH_LENGTH 64 /* D-Bus path */
#define NETWORK_PATH "/org/bluez/network"

int bnep_init(const char *panu_script, const char *gn_script,
		const char *nap_script);
int bnep_cleanup(void);

uint16_t bnep_service_id(const char *svc);
const char *bnep_uuid(uint16_t id);
const char *bnep_name(uint16_t id);

int bnep_kill_connection(bdaddr_t *dst);
int bnep_kill_all_connections(void);

int bnep_connadd(int sk, uint16_t role, char *dev);
int bnep_if_up(const char *devname, uint16_t id);
int bnep_if_down(const char *devname);

int read_remote_name(bdaddr_t *src, bdaddr_t *dst, char *buf, size_t size);
