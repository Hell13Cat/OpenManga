package org.nv95.openmanga.preview;

import android.app.LoaderManager;
import android.content.Intent;
import android.content.Loader;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.TabLayout;
import android.support.v4.view.ViewPager;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RatingBar;
import android.widget.TextView;

import org.nv95.openmanga.AppBaseActivity;
import org.nv95.openmanga.R;
import org.nv95.openmanga.common.ViewPagerAdapter;
import org.nv95.openmanga.common.dialogs.FavouriteDialog;
import org.nv95.openmanga.common.utils.ErrorUtils;
import org.nv95.openmanga.common.utils.ImageUtils;
import org.nv95.openmanga.common.utils.TextUtils;
import org.nv95.openmanga.core.MangaStatus;
import org.nv95.openmanga.core.ObjectWrapper;
import org.nv95.openmanga.core.models.Category;
import org.nv95.openmanga.core.models.MangaChapter;
import org.nv95.openmanga.core.models.MangaDetails;
import org.nv95.openmanga.core.models.MangaFavourite;
import org.nv95.openmanga.core.models.MangaHeader;
import org.nv95.openmanga.core.models.MangaHistory;
import org.nv95.openmanga.core.providers.MangaProvider;
import org.nv95.openmanga.core.storage.db.FavouritesRepository;
import org.nv95.openmanga.core.storage.db.HistoryRepository;
import org.nv95.openmanga.reader.ReaderActivity;

/**
 * Created by koitharu on 26.12.17.
 */

public final class PreviewActivity extends AppBaseActivity implements LoaderManager.LoaderCallbacks<ObjectWrapper<MangaDetails>>,ChaptersListAdapter.OnChapterClickListener, View.OnClickListener, FavouriteDialog.OnFavouriteListener {

	//views
	//activity
	private ViewPager mPager;
	private ProgressBar mProgressBar;
	private TabLayout mTabs;
	//tab 1 - description
	private ImageView mImageViewCover;
	private TextView mTextViewSummary;
	private RatingBar mRatingBar;
	private TextView mTextViewGenres;
	private TextView mTextViewDescription;
	private Button mButtonRead;
	private ImageButton mButtonFavourite;
	//tab 2 - chapters
	private RecyclerView mRecyclerViewChapters;
	private TextView mTextViewChaptersHolder;
	//data
	private MangaProvider mProvider;
	private MangaHeader mMangaHeader;
	@Nullable
	private MangaDetails mMangaDetails;
	private ViewPagerAdapter mPagerAdapter;
	@Nullable
	ChaptersListAdapter mChaptersAdapter;
	private HistoryRepository mHistory;
	private FavouritesRepository mFavourites;

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_preview);
		setSupportActionBar(R.id.toolbar);
		enableHomeAsUp();

		mPager = findViewById(R.id.pager);
		mProgressBar = findViewById(R.id.progressBar);
		mTabs = findViewById(R.id.tabs);

		View page1 = getLayoutInflater().inflate(R.layout.page_manga_details, mPager, false);
		mImageViewCover = page1.findViewById(R.id.imageView_cover);
		mTextViewSummary = page1.findViewById(R.id.textView_summary);
		mRatingBar = page1.findViewById(R.id.ratingBar);
		mTextViewDescription = page1.findViewById(R.id.textView_description);
		mTextViewGenres = page1.findViewById(R.id.textView_genres);
		mButtonRead = page1.findViewById(R.id.button_read);
		mButtonFavourite = page1.findViewById(R.id.button_favourite);
		View page2 = getLayoutInflater().inflate(R.layout.page_manga_chapters, mPager, false);
		mRecyclerViewChapters = page2.findViewById(R.id.recyclerView_chapters);
		mRecyclerViewChapters.addItemDecoration(new DividerItemDecoration(this, LinearLayoutManager.VERTICAL));
		mTextViewChaptersHolder = page2.findViewById(R.id.textView_chapters_holder);
		mPagerAdapter = new ViewPagerAdapter(page1, page2);
		mPager.setAdapter(mPagerAdapter);
		mTabs.setupWithViewPager(mPager);
		mButtonRead.setOnClickListener(this);
		mButtonFavourite.setOnClickListener(this);

		mMangaHeader = getIntent().getParcelableExtra("manga");
		mMangaDetails = null;
		assert mMangaHeader != null;
		mChaptersAdapter = null;
		mProvider = MangaProvider.get(this, mMangaHeader.provider);
		mHistory = new HistoryRepository(this);
		mFavourites = new FavouritesRepository(this);

		updateContent();

		Bundle args = new Bundle(1);
		args.putParcelable("manga", mMangaHeader);
		getLoaderManager().initLoader(0, args, this).forceLoad();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.options_preview, menu);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	protected void onResume() {
		super.onResume();
		final MangaHistory history = mHistory.find(mMangaHeader);
		if (history != null) {
			if (mChaptersAdapter != null) {
				mChaptersAdapter.setCurrentChapterId(history.chapterId);
				mChaptersAdapter.notifyDataSetChanged();
			}
			mButtonRead.setText(R.string.continue_reading);
		}
		final MangaFavourite favourite = mFavourites.get(mMangaHeader);
		mButtonFavourite.setImageResource(favourite == null ? R.drawable.ic_favorite_outline_light : R.drawable.ic_favorite_light);
	}

	@NonNull
	@Override
	public Loader<ObjectWrapper<MangaDetails>> onCreateLoader(int id, Bundle args) {
		return new MangaDetailsLoader(this, args.<MangaHeader>getParcelable("manga"));
	}

	@Override
	public void onLoadFinished(Loader<ObjectWrapper<MangaDetails>> loader, ObjectWrapper<MangaDetails> data) {
		mProgressBar.setVisibility(View.GONE);
		if (data.isSuccess()) {
			mMangaDetails = data.get();
			assert mMangaDetails != null;
			updateContent();
			mChaptersAdapter = new ChaptersListAdapter(this, mMangaDetails.chapters, this);
			final MangaHistory history = mHistory.find(mMangaHeader);
			if (history == null) {
				mChaptersAdapter.setCurrentChapterId(0);
			} else {
				mChaptersAdapter.setCurrentChapterId(history.chapterId);
			}
			mRecyclerViewChapters.setAdapter(mChaptersAdapter);
			/*if (history != null) {
				int pos = CollectionsUtils.findChapterPositionById(mMangaDetails.chapters, history.chapterId);
				if (pos != -1 && LayoutUtils.findLastVisibleItemPosition(mRecyclerViewChapters) > pos) {
					mRecyclerViewChapters.scrollToPosition(Math.max(0, pos - 3));
				}
			}*/
			mTextViewChaptersHolder.setText(R.string.no_chapters_found);
			mTextViewChaptersHolder.setVisibility(mMangaDetails.chapters.isEmpty() ? View.VISIBLE : View.GONE);
		} else {
			mTextViewChaptersHolder.setText(R.string.failed_to_load_chapters);
			mTextViewChaptersHolder.setVisibility(View.VISIBLE);
			mTextViewDescription.setText(ErrorUtils.getErrorMessage(data.getError()));
		}
	}

	@Override
	public void onLoaderReset(Loader<ObjectWrapper<MangaDetails>> loader) {

	}

	@Override
	public void onChapterClick(int pos, MangaChapter chapter) {
		startActivity(new Intent(this, ReaderActivity.class)
				.putExtra("manga", mMangaDetails)
				.putExtra("chapter", chapter));
	}

	@Override
	public boolean onChapterLongClick(int pos, MangaChapter chapter) {
		return false;
	}

	private void updateContent() {
		if (mMangaDetails == null) { //full info wasn't loaded yet
			ImageUtils.setThumbnail(mImageViewCover, mMangaHeader.thumbnail, MangaProvider.getDomain(mMangaHeader.provider));
			setTitle(mMangaHeader.name);
			setSubtitle(mMangaHeader.summary);
			mTextViewGenres.setText(mMangaHeader.genres);
			if (mMangaHeader.rating == 0) {
				mRatingBar.setVisibility(View.GONE);
			} else {
				mRatingBar.setVisibility(View.VISIBLE);
				mRatingBar.setRating(mMangaHeader.rating / 20);
			}
			mTextViewSummary.setText(formatSummary(null, -1, mProvider.getName(), mMangaHeader.status));
		} else {
			ImageUtils.updateImage(mImageViewCover, mMangaDetails.cover, MangaProvider.getDomain(mMangaDetails.provider));
			setTitle(mMangaDetails.name);
			setSubtitle(mMangaDetails.summary);
			mTextViewGenres.setText(mMangaDetails.genres);
			mTextViewDescription.setText(TextUtils.fromHtmlCompat(mMangaDetails.description));
			if (mMangaDetails.rating != 0) {
				mRatingBar.setVisibility(View.VISIBLE);
				mRatingBar.setRating(mMangaDetails.rating / 20);
			}
			mTextViewSummary.setText(formatSummary(mMangaDetails.author, mMangaDetails.chapters.size(), mProvider.getName(), mMangaDetails.status));
		}
	}

	@NonNull
	private CharSequence formatSummary(@Nullable String author, int chapters, String provider, @MangaStatus int status) {
		final StringBuilder builder = new StringBuilder();
		if (!android.text.TextUtils.isEmpty(author)) {
			builder.append("<b>").append(getString(R.string.author_)).append("</b> ");
			builder.append(author).append("<br/>");
		}
		builder.append("<b>").append(getString(R.string.chapters_count_)).append("</b> ");
		if (chapters == -1) {
			builder.append("?");
		} else {
			builder.append(chapters);
		}
		builder.append("<br/>").append("<b>").append(getString(R.string.source_)).append("</b> ");
		builder.append(provider);
		switch (status) {
			case MangaStatus.STATUS_COMPLETED:
				builder.append("<br/>").append(getString(R.string.status_completed));
				break;
			case MangaStatus.STATUS_ONGOING:
				builder.append("<br/>").append(getString(R.string.status_ongoing));
				break;
			case MangaStatus.STATUS_UNKNOWN:
				break;
		}
		return TextUtils.fromHtmlCompat(builder.toString());
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
			case R.id.button_read:
				startActivity(new Intent(this, ReaderActivity.class)
						.setAction(ReaderActivity.ACTION_READING_CONTINUE)
						.putExtra("manga", mMangaDetails));
				break;
			case R.id.button_favourite:
				if (mMangaDetails != null) {
					new FavouriteDialog(this, mMangaDetails)
							.setListener(this)
							.show();
				}
				break;
		}
	}

	@Override
	public void onFavouritesChanged(MangaDetails manga, @Nullable Category category) {
		mButtonFavourite.setImageResource(category == null ? R.drawable.ic_favorite_outline_light : R.drawable.ic_favorite_light);
	}
}
