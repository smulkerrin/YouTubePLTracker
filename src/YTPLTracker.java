//YouTube Data API program built on the YouTube sample program skeleton, used to collect
//and track playlist and subscription data relating to a user's YouTube account.

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.DataStoreFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.YouTubeScopes;
import com.google.api.services.youtube.model.*;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Main class for the YouTube Data API command line sample. Demonstrates how to
 * make an authenticated API call using OAuth 2 helper classes.
 */
public class YTPLTracker
{
	/**
	 * Be sure to specify the name of your application. If the application name
	 * is {@code null} or blank, the application will log a warning. Suggested
	 * format is "MyCompany-ProductName/1.0".
	 */
	private static final String APPLICATION_NAME = "SM-YouTubePlaylistTracker";

	/** Directory to store user credentials. */
	private static final java.io.File DATA_STORE_DIR = new java.io.File(
			System.getProperty( "user.home" ), ".store/youtube_sample" );

	/**
	 * Global instance of the {@link DataStoreFactory}. The best practice is to
	 * make it a single globally shared instance across your application.
	 */
	private static FileDataStoreFactory dataStoreFactory;

	/** Global instance of the JSON factory. */
	private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

	/** Global instance of the HTTP transport. */
	private static HttpTransport httpTransport;

	private static YouTube client;

	/** Authorizes the installed application to access user's protected data. */
	private static Credential authorize() throws Exception
	{
		// load client secrets
		GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(
				JSON_FACTORY,
				new InputStreamReader( YTPLTracker.class
						.getResourceAsStream( "/client_secrets.json" ) ) );
		if( clientSecrets.getDetails().getClientId().startsWith( "Enter" )
				|| clientSecrets.getDetails().getClientSecret().startsWith( "Enter " ) )
		{
			System.out
					.println( "Overwrite the src/main/resources/client_secrets.json file with the client secrets file "
							+ "you downloaded from the Quickstart tool or manually enter your Client ID and Secret "
							+ "from https://code.google.com/apis/console/?api=youtube#project:973907235197 "
							+ "into src/main/resources/client_secrets.json" );
			System.exit( 1 );
		}

		// Set up authorization code flow.
		// Ask for only the permissions you need. Asking for more permissions
		// will
		// reduce the number of users who finish the process for giving you
		// access
		// to their accounts. It will also increase the amount of effort you
		// will
		// have to spend explaining to users what you are doing with their data.
		// Here we are listing all of the available scopes. You should remove
		// scopes
		// that you are not actually using.
		Set< String > scopes = new HashSet< String >();
		scopes.add( YouTubeScopes.YOUTUBE );
		scopes.add( YouTubeScopes.YOUTUBE_READONLY );
		scopes.add( YouTubeScopes.YOUTUBE_UPLOAD );
		scopes.add( YouTubeScopes.YOUTUBEPARTNER );
		scopes.add( YouTubeScopes.YOUTUBEPARTNER_CHANNEL_AUDIT );

		GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder( httpTransport,
				JSON_FACTORY, clientSecrets, scopes ).setDataStoreFactory( dataStoreFactory )
				.build();
		// authorize
		return new AuthorizationCodeInstalledApp( flow, new LocalServerReceiver() )
				.authorize( "user" );
	}

	public static void main( String[] args )
	{
		try
		{
			// initialize the transport
			httpTransport = GoogleNetHttpTransport.newTrustedTransport();

			// initialize the data store factory
			dataStoreFactory = new FileDataStoreFactory( DATA_STORE_DIR );

			// authorization
			Credential credential = authorize();

			// set up global YouTube instance
			client = new YouTube.Builder( httpTransport, JSON_FACTORY, credential )
					.setApplicationName( APPLICATION_NAME ).build();

			// ////////////////////////////////////////////////////////////////////

			YTPLModel model = new YTPLModel();
			YTPLView view = new YTPLView( model );

			// If user specifies a txt file from an older run of the program as
			// a console argument...

			boolean validInputFile = false;
			if( args.length > 0 )
			{
				validInputFile = model.loadPrev( args[0] );
			}

			// Get channel info
			YouTube.Channels.List channelRequest = client.channels().list( "snippet" );
			channelRequest.setMine( true );

			ChannelListResponse channelResult = channelRequest.execute();

			List< Channel > channelsList = channelResult.getItems();

			// Store name of user's channel
			String userChannel = channelsList.get( 0 ).getSnippet().getTitle();

			channelRequest = client.channels().list( "contentDetails" );
			channelRequest.setMine( true );
			channelResult = channelRequest.execute();

			channelsList = channelResult.getItems();

			// Retrieve IDs of Favorites and Watch Later playlists
			String favesID = channelsList.get( 0 ).getContentDetails().getRelatedPlaylists()
					.getFavorites();
			String wLaterID = channelsList.get( 0 ).getContentDetails().getRelatedPlaylists()
					.getWatchLater();

			// Get playlist info
			YouTube.Playlists.List playListRequest = client.playlists().list( "snippet" );
			playListRequest.setMine( true );

			playListRequest.setMaxResults( Long.valueOf( 50 ) );

			PlaylistListResponse playlistResult;

			ArrayList< Playlist > playlistsList = new ArrayList< Playlist >();

			{
				String nextToken = "";

				// Scroll through result pages
				do
				{
					playListRequest.setPageToken( nextToken );
					playlistResult = playListRequest.execute();

					playlistsList.addAll( playlistResult.getItems() );

					nextToken = playlistResult.getNextPageToken();
				}
				while( nextToken != null );
			}

			// STORE PLAYLIST + SUB DATA IN CONTAINERS
			YouTube.PlaylistItems.List plItemsRequest = client.playlistItems().list( "snippet" );

			// Store Favourites Info
			{
				plItemsRequest.setPlaylistId( favesID );
				plItemsRequest.setMaxResults( Long.valueOf( 3 ) );

				PlaylistItemListResponse plItemsResult;

				ArrayList< PlaylistItem > plItemsList = new ArrayList< PlaylistItem >();

				String nextToken = "";

				// Scroll through result pages
				do
				{
					plItemsRequest.setPageToken( nextToken );
					plItemsResult = plItemsRequest.execute();

					plItemsList.addAll( plItemsResult.getItems() );

					nextToken = plItemsResult.getNextPageToken();
				}
				while( nextToken != null );

				for( PlaylistItem plI : plItemsList )
				{
					// Get video title
					String vTitle = plI.getSnippet().getTitle();

					// Get video channel
					String vId = plI.getSnippet().getResourceId().getVideoId();
					YouTube.Videos.List vidRequest = client.videos().list( "snippet" );
					vidRequest.setId( vId );

					vidRequest.setMaxResults( Long.valueOf( 3 ) );

					VideoListResponse vidResult = vidRequest.execute();

					List< Video > vidList = vidResult.getItems();

					String vChannel = vidList.get( 0 ).getSnippet().getChannelTitle();

					// Store video data in playlist video list
					model.addFavData( new VidEntry( vTitle, vChannel ) );
				}
			}

			System.out.println( "Favourites Gathered..." );

			// Store Watch Later Info
			{
				plItemsRequest.setPlaylistId( wLaterID );
				plItemsRequest.setMaxResults( Long.valueOf( 3 ) );

				PlaylistItemListResponse plItemsResult;
				ArrayList< PlaylistItem > plItemsList = new ArrayList< PlaylistItem >();

				String nextToken = "";

				// Scroll through result pages
				do
				{
					plItemsRequest.setPageToken( nextToken );
					plItemsResult = plItemsRequest.execute();

					plItemsList.addAll( plItemsResult.getItems() );

					nextToken = plItemsResult.getNextPageToken();
				}
				while( nextToken != null );

				for( PlaylistItem plI : plItemsList )
				{
					// Get video title
					String vTitle = plI.getSnippet().getTitle();

					// Get video channel
					String vId = plI.getSnippet().getResourceId().getVideoId();
					YouTube.Videos.List vidRequest = client.videos().list( "snippet" );
					vidRequest.setId( vId );
					vidRequest.setMaxResults( Long.valueOf( 3 ) );

					VideoListResponse vidResult = vidRequest.execute();

					List< Video > vidList = vidResult.getItems();

					String vChannel = vidList.get( 0 ).getSnippet().getChannelTitle();

					// Store video data in playlist video list
					model.addWLData( new VidEntry( vTitle, vChannel ) );
				}
			}

			System.out.println( "Watch Later Gathered..." );

			YouTube.PlaylistItems.List plItemsRequest2 = client.playlistItems().list( "snippet" );

			// Store Playlists Info
			for( Playlist pl : playlistsList )
			{
				plItemsRequest2.setPlaylistId( pl.getId() );
				plItemsRequest2.setMaxResults( Long.valueOf( 20 ) );
				PlaylistItemListResponse plItemsResult;
				ArrayList< PlaylistItem > plItemsList = new ArrayList< PlaylistItem >();

				// Create ArrayList to store video data
				ArrayList< VidEntry > plVids = new ArrayList< VidEntry >();

				String nextToken = "";

				// Scroll through result pages
				do
				{
					plItemsRequest2.setPageToken( nextToken );
					plItemsResult = plItemsRequest2.execute();

					plItemsList.addAll( plItemsResult.getItems() );

					nextToken = plItemsResult.getNextPageToken();
				}
				while( nextToken != null );

				for( PlaylistItem plI : plItemsList )
				{
					// Get video title
					String vTitle = plI.getSnippet().getTitle();

					// Get video channel
					String vId = plI.getSnippet().getResourceId().getVideoId();
					YouTube.Videos.List vidRequest = client.videos().list( "snippet" );
					vidRequest.setId( vId );
					// vidRequest.setMaxResults( Long.valueOf( 3 ) );

					VideoListResponse vidResult = vidRequest.execute();

					List< Video > vidList = vidResult.getItems();

					String vChannel = vidList.get( 0 ).getSnippet().getChannelTitle();

					// Store video data in playlist video list
					plVids.add( new VidEntry( vTitle, vChannel ) );
				}

				// Store playlist data
				model.addPLData( pl.getSnippet().getTitle(), plVids );
			}

			System.out.println( "Playlists Gathered..." );

			// //Store Subscription info
			YouTube.Subscriptions.List subRequest = client.subscriptions().list( "snippet" );
			subRequest.setMine( true );
			subRequest.setMaxResults( Long.valueOf( 3 ) );

			SubscriptionListResponse subResult;// = subRequest.execute();

			ArrayList< Subscription > subList = new ArrayList< Subscription >();// subResult.getItems();

			String nextToken = "";

			// Scroll through result pages
			do
			{
				subRequest.setPageToken( nextToken );
				subResult = subRequest.execute();

				subList.addAll( subResult.getItems() );

				nextToken = subResult.getNextPageToken();
			}
			while( nextToken != null );

			for( Subscription s : subList )
			{
				String subTitle = s.getSnippet().getTitle();

				model.addSub( subTitle );
			}

			System.out.println( "Subscriptions Gathered..." );
			System.out.println();
			System.out.println( "Saving Data..." );

			view.saveToText( userChannel, args[0], validInputFile, model );
		}
		catch( IOException e )
		{
			System.err.println( e.getMessage() );
		}
		catch( Throwable t )
		{
			t.printStackTrace();
		}
	}
}