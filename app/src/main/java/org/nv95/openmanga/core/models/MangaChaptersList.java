package org.nv95.openmanga.core.models;

import android.support.annotation.Nullable;

import java.util.ArrayList;

/**
 * Created by koitharu on 21.12.17.
 */

public class MangaChaptersList extends ArrayList<MangaChapter> {

	public MangaChaptersList() {
	}

	public MangaChaptersList(int initialCapacity) {
		super(initialCapacity);
	}

	public MangaChaptersList(ArrayList<MangaChapter> source) {
		super(source);
	}

	@Nullable
	public MangaChapter findItemById(long id) {
		for (MangaChapter o : this) {
			if (o != null && o.id == id) {
				return o;
			}
		}
		return null;
	}
}
