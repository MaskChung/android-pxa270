package android.test;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.Context;
import android.test.mock.MockContext;
import android.test.mock.MockContentResolver;
import android.database.DatabaseUtils;

/**
 * If you would like to test a single content provider with an
 * {@link InstrumentationTestCase}, this provides some of the boiler plate in {@link #setUp} and
 * {@link #tearDown}.
 */
public abstract class ProviderTestCase<T extends ContentProvider>
        extends InstrumentationTestCase {

    Class<T> mProviderClass;
    String mProviderAuthority;

    private IsolatedContext mProviderContext;
    private MockContentResolver mResolver;

    public ProviderTestCase(Class<T> providerClass, String providerAuthority) {
        mProviderClass = providerClass;
        mProviderAuthority = providerAuthority;
    }

    /**
     * The content provider that will be set up for use in each test method.
     */
    private T mProvider;

    public T getProvider() {
        return mProvider;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mResolver = new MockContentResolver();
        final String filenamePrefix = "test.";
        RenamingDelegatingContext targetContextWrapper = new RenamingDelegatingContext(
                new MockContext(), // The context that most methods are delegated to
                getInstrumentation().getTargetContext(), // The context that file methods are delegated to
                filenamePrefix);
        mProviderContext = new IsolatedContext(mResolver, targetContextWrapper);

        mProvider = mProviderClass.newInstance();
        mProvider.attachInfo(mProviderContext, null);
        assertNotNull(mProvider);
        mResolver.addProvider(mProviderAuthority, getProvider());
    }

    public MockContentResolver getMockContentResolver() {
        return mResolver;
    }

    public IsolatedContext getMockContext() {
        return mProviderContext;
    }

    public static <T extends ContentProvider> ContentResolver newResolverWithContentProviderFromSql(
            Context targetContext, Class<T> providerClass, String authority,
            String databaseName, int databaseVersion, String sql)
            throws IllegalAccessException, InstantiationException {
        final String filenamePrefix = "test.";
        MockContentResolver mockContentResolver = new MockContentResolver();
        RenamingDelegatingContext targetContextWrapper = new RenamingDelegatingContext(
                new MockContext(), // The context that most methods are delegated to
                targetContext, // The context that file methods are delegated to
                filenamePrefix);
        Context context = new IsolatedContext(
                mockContentResolver, targetContextWrapper);
        DatabaseUtils.createDbFromSqlStatements(context, databaseName, databaseVersion, sql);

        T provider = providerClass.newInstance();
        provider.attachInfo(context, null);
        MockContentResolver resolver = new MockContentResolver();
        resolver.addProvider(authority, provider);

        return resolver;
    }
}
