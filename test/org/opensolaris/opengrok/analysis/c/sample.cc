/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at usr/src/OPENSOLARIS.LICENSE
 * or http://www.opensolaris.org/os/licensing.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at usr/src/OPENSOLARIS.LICENSE.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information: Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 */
/*
 * Copyright (c) 2000-2001 by Sun Microsystems, Inc.
 * All rights reserved.
 */

#pragma ident	"%Z%%M%	%I%	%E% SMI"

#include <stdlib.h>
#include <string.h>

#include "Ancestor.h"

/* ========================================================================= */
/* Ancestor object definitions. */

Ancestor::Ancestor(Str field, fru_tag_t t, const fru_regdef_t *d)
	: field_name(field),
    tag(t),
    def(d),
    numInstances(0),
    numBufs(1),
    next(NULL)
{
	offsets = (uint32_t *)malloc(sizeof (uint32_t)
					* ANCESTOR_INST_BUF_SIZE);
	paths = (char **)malloc(sizeof (char *)
					* ANCESTOR_INST_BUF_SIZE);
}

Ancestor::~Ancestor()
{
	free(offsets);
	if (paths != NULL) {
		for (int i = 0; i < numInstances; i++) {
			free(paths[i]);
		}
	}
	free(paths);
	delete next;
}

/*
void
Ancestor::print(void)
{
	fprintf(stderr, "Ancestor Information\n");
	fprintf(stderr, "Tag Name: %s\n", def->name);
	fprintf(stderr, "Tag Type: %s\n",
			get_tagtype_str(get_tag_type(&tag)));
	fprintf(stderr, "Num instances: %d\n", numInstances);
	fprintf(stderr, "   offsets:\n");
	for (int i = 0; i < numInstances; i++) {
		fprintf(stderr, "   %d\n", offsets[i]);
	}

	if (next != NULL) {
		next->print();
	}
}
*/

void
Ancestor::addInstance(const char *path, uint32_t offset)
{
	if (numInstances >= ANCESTOR_INST_BUF_SIZE) {
		numBufs++;
		offsets = (uint32_t *)realloc(offsets,
			(sizeof (uint32_t) *
				(ANCESTOR_INST_BUF_SIZE * numBufs)));
		paths = (char **)realloc(offsets,
			(sizeof (char *) *
				(ANCESTOR_INST_BUF_SIZE * numBufs)));
	}
	offsets[numInstances] = offset;
	paths[numInstances++] = strdup(path);
}

Str
Ancestor::getFieldName(void)
{
	return (field_name);
}

fru_tag_t
Ancestor::getTag(void)
{
	return (tag);
}

const fru_regdef_t *
Ancestor::getDef(void)
{
	return (def);
}

int
Ancestor::getNumInstances(void)
{
	return (numInstances);
}

uint32_t
Ancestor::getInstOffset(int num)
{
	if (num < numInstances)
		return (offsets[num]);
	else
		return (offsets[numInstances]);
}

const char *
Ancestor::getPath(int num)
{
	if (num < numInstances)
		return (paths[num]);
	else
		return (paths[numInstances]);
}


Ancestor *
Ancestor::listTaggedAncestors(char *element)
{
	Ancestor *rc = NULL;
	fru_regdef_t *def = NULL;
	int i = 0;

	unsigned int number = 0;
	char **data_elems = fru_reg_list_entries(&number);

	if (data_elems == NULL) {
		return (NULL);
	}

	// look through all the elements.
	for (i = 0; i < number; i++) {
		def = (fru_regdef_t *)
			fru_reg_lookup_def_by_name(data_elems[i]);
		Ancestor *ant = createTaggedAncestor(def, element);
		if (ant != NULL) {
			if (rc == NULL) {
				rc = ant;
			} else {
				Ancestor *tmp = rc;
				while (tmp->next != NULL) {
					tmp = tmp->next;
				}
				tmp->next = ant;
			}
		}
	}

	for (i = 0; i < number; i++) {
		free(data_elems[i]);
	}
	free(data_elems);

	return (rc);
}

Ancestor *
Ancestor::createTaggedAncestor(const fru_regdef_t *def, Str element)
{
	// ancestors have to be tagged.
	if (def->tagType == FRU_X)
		return (NULL);

	fru_tag_t tag;
	mk_tag(def->tagType, def->tagDense, def->payloadLen, &tag);
	Ancestor *rc = new Ancestor(element, tag, def);

	if (element.compare(def->name) == 0) {
		rc->addInstance("", 0);
		return (rc);
	}

	int found = 0;
	if (def->dataType == FDTYPE_Record) {
		uint32_t offset = 0;
		for (int i = 0; i < def->enumCount; i++) {
			const fru_regdef_t *tmp
				= fru_reg_lookup_def_by_name
					((char *)def->enumTable[i].text);
			Str path = "/";
			path << def->name;
			int f = definitionContains(tmp, def, element,
							offset, rc, path);
			if (f == 1) found = 1; // found needs to latch at one.
				offset += tmp->payloadLen;
		}
	}

	if (!found) {
		delete rc;
		return (NULL);
	}

	return (rc);
}

int
Ancestor::definitionContains(const fru_regdef_t *def,
				const fru_regdef_t *parent_def,
				Str element,
				uint32_t offset,
				Ancestor *ant,
				Str path)
{
	if (element.compare(def->name) == 0) {
		if (parent_def->iterationType != FRU_NOT_ITERATED) {
			offset += 4;
			for (int i = 0; i < parent_def->iterationCount; i++) {
				Str tmp = path;
				tmp << "[" << i << "]/";
				ant->addInstance(tmp.peak(), offset);
				offset += (parent_def->payloadLen - 4) /
						parent_def->iterationCount;
			}
		} else {
			path << "/";
			ant->addInstance(path.peak(), offset);
		}
		return (1);
	}

	int found = 0;
	if (def->dataType == FDTYPE_Record) {
		for (int i = 0; i < def->enumCount; i++) {
			const fru_regdef_t *tmp
				= fru_reg_lookup_def_by_name
					((char *)def->enumTable[i].text);
			Str newPath = path;
			newPath << "/" << def->name;
			int f = definitionContains(tmp, def, element,
							offset, ant, newPath);
			if (f == 1) found = 1; // found needs to latch at one.
				offset += tmp->payloadLen;
		}
	}

	return (found);
}
