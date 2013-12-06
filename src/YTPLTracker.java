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

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

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
	private static final String APPLICATION_NAME = "";

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

			// Containers to store retrieved/loaded data
			ArrayList< VidEntry > favData = new ArrayList< VidEntry >();
			ArrayList< VidEntry > prevFavData = new ArrayList< VidEntry >();

			ArrayList< VidEntry > wlData = new ArrayList< VidEntry >();
			ArrayList< VidEntry > prevWLData = new ArrayList< VidEntry >();

			ArrayList< String > subNames = new ArrayList< String >();
			ArrayList< String > prevSubNames = new ArrayList< String >();

			HashMap< String, ArrayList< VidEntry > > plData = new HashMap< String, ArrayList< VidEntry > >();
			HashMap< String, ArrayList< VidEntry > > prevPLData = new HashMap< String, ArrayList< VidEntry > >();

			// If user specifies a txt file from an older run of the program as
			// a console argument...
			boolean validInputFile = false;
			if( args.length > 0 )
			{
				try
				{
					// Load specified file
					BufferedReader in = new BufferedReader( new FileReader( args[0] ) );

					// Read in data and populate containers
					String line = in.readLine();

					// Find favorites section
					while( in.ready() )
					{
						if( line.equals( "[Favorites]" ) )
						{
							//Skip list length
							in.readLine();
							while( in.ready() )
							{
								line = in.readLine();

								// If reached the end of favorites list, stop
								if( line.isEmpty() )
								{
									break;
								}

								// Otherwise...
								String channel = line.substring( line.indexOf( "Channel:" )
										+ "Channel:".length(), line.indexOf( ']' ) );
								String video = line.substring( line.indexOf( ']' ) + 1 );

								prevFavData.add( new VidEntry( video, channel ) );
							}

							break;
						}

						line = in.readLine();
					}

					// Find watch later section
					while( in.ready() )
					{
						if( line.equals( "[Watch Later]" ) )
						{
							//Skip list length
							in.readLine();
							while( in.ready() )
							{
								line = in.readLine();

								// If reached the end of watch later list, stop
								if( line.isEmpty() )
								{
									break;
								}

								// Otherwise...
								String channel = line.substring( line.indexOf( "Channel:" )
										+ "Channel:".length(), line.indexOf( ']' ) );
								String video = line.substring( line.indexOf( ']' ) + 1 );

								prevWLData.add( new VidEntry( video, channel ) );
							}

							break;
						}

						line = in.readLine();
					}

					// Find playlist sections
					while( in.ready() )
					{
						if( line.contains( "[Playlist]" ) )
						{
							// Store playlist title
							String plTitle = line.substring( line.indexOf( ']' ) + 1 );

							// Create list of video entries for playlist
							ArrayList< VidEntry > plVidList = new ArrayList< VidEntry >();

							//Skip list length
							in.readLine();
							
							while( in.ready() )
							{
								line = in.readLine();

								// If reached the end of playlist, stop
								if( line.isEmpty() )
								{
									break;
								}

								// Otherwise...
								String channel = line.substring( line.indexOf( "Channel:" )
										+ "Channel:".length(), line.indexOf( ']' ) );
								String video = line.substring( line.indexOf( ']' ) + 1 );

								plVidList.add( new VidEntry( video, channel ) );
							}

							prevPLData.put( plTitle, plVidList );
						}
						else if( line.startsWith( "[" ) )
						{
							break;
						}

						line = in.readLine();
					}

					// Find subscriptions section
					while( in.ready() )
					{
						if( line.equals( "[Subscriptions]" ) )
						{
							//Skip list length
							in.readLine();
							
							while( in.ready() )
							{
								line = in.readLine();

								// If reached the end of subs list, stop
								if( line.isEmpty() )
								{
									break;
								}

								// Otherwise...
								String sub = line;

								prevSubNames.add( sub );
							}

							break;
						}

						line = in.readLine();
					}

					validInputFile = true;

					// Close file
					in.close();
				}
				catch( IOException e )
				{
					e.printStackTrace();
				}
			}

			// Get channel info
			YouTube.Channels.List channelRequest = client.channels().list( "snippet" );
			channelRequest.setMine( true );

			ChannelListResponse channelResult = channelRequest.execute();

			List< Channel > channelsList = channelResult.getItems();
			
			//Store name of user's channel
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

			playListRequest.setMaxResults( Long.valueOf( 50 )  );

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
					favData.add( new VidEntry( vTitle, vChannel ) );
				}
			}
			
			System.out.println( "Favourites Gathered..." );

			//Store Watch Later Info
			{
				plItemsRequest.setPlaylistId( wLaterID );
				plItemsRequest.setMaxResults( Long.valueOf( 3 ) );

				PlaylistItemListResponse plItemsResult;
				ArrayList< PlaylistItem > plItemsList = new ArrayList< PlaylistItem >();

				String nextToken = "";

				//Scroll through result pages
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
					wlData.add( new VidEntry( vTitle, vChannel ) );
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

				//Scroll through result pages
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
					//vidRequest.setMaxResults( Long.valueOf( 3 ) );

					VideoListResponse vidResult = vidRequest.execute();

					List< Video > vidList = vidResult.getItems();

					String vChannel = vidList.get( 0 ).getSnippet().getChannelTitle();

					// Store video data in playlist video list
					plVids.add( new VidEntry( vTitle, vChannel ) );
				}

				// Store playlist data
				plData.put( pl.getSnippet().getTitle(), plVids );
			}

			System.out.println( "Playlists Gathered..." );
			
			// //Store Subscription info
			YouTube.Subscriptions.List subRequest = client.subscriptions().list( "snippet" );
			subRequest.setMine( true );
			subRequest.setMaxResults( Long.valueOf( 3 ) );

			SubscriptionListResponse subResult;// = subRequest.execute();

			ArrayList< Subscription > subList = new ArrayList< Subscription >();// subResult.getItems();

			String nextToken = "";

			//Scroll through result pages
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

				subNames.add( subTitle );
			}
			
			System.out.println( "Subscriptions Gathered..." );

			System.out.println();
			System.out.println( "Saving Data..." );
			
			// Save retrieved data in text file
			Date date = new Date();
			SimpleDateFormat sDF = new SimpleDateFormat( "ddMMyyHHmmss" );
			
			String outputFileName = "YTPT" + sDF.format( date ) + ".txt";

			PrintWriter out = new PrintWriter( new FileWriter( outputFileName ) );

			// Channel data
			out.println( "Data collected from channel " + userChannel + ":" );
			out.println( "=====" );
			out.println();

			// Favorites
			out.println( "[Favorites]" );
			out.println( "(" + favData.size() + ")" );
			for( VidEntry vE : favData )
			{
				out.println( "[Channel:" + vE.getChannel() + "]" + vE.getTitle() );
			}
			out.println();

			// Watch Later
			out.println( "[Watch Later]" );
			out.println( "(" + wlData.size() + ")" );
			for( VidEntry vE : wlData )
			{
				out.println( "[Channel:" + vE.getChannel() + "]" + vE.getTitle() );
			}
			out.println();

			// Playlists
			for( Map.Entry< String, ArrayList< VidEntry > > entry : plData.entrySet() )
			{
				String plTitle = entry.getKey();
				ArrayList< VidEntry > vidList = entry.getValue();

				out.println( "[Playlist]" + plTitle );
				out.println( "(" + vidList.size() + ")" );

				for( VidEntry vE : vidList )
				{
					out.println( "[Channel:" + vE.getChannel() + "]" + vE.getTitle() );
				}

				out.println();
			}

			// Subscriptions
			out.println( "[Subscriptions]" );
			out.println( "(" + subNames.size() + ")" );
			for( String s : subNames )
			{
				out.println( s );
			}

			// Output txt file report on differences between program runs, if
			// old run passed in
			if( validInputFile )
			{
				out.println( "" );
				out.println( "=====" );
				out.println( "Differences detected from previous run " + args[0] + ":" );
				out.println( "=====" );
				out.println();

				// Faves
				out.println( "[Favorites]" );
				ArrayList< VidEntry > removedFavs = new ArrayList< VidEntry >( prevFavData );
				removedFavs.removeAll( favData );
				out.println( "Removed" + "(" + removedFavs.size() + "): " );
				for( VidEntry vE : removedFavs )
				{
					out.println( "[Channel:" + vE.getChannel() + "]" + vE.getTitle() );
				}
				out.println();

				ArrayList< VidEntry > newFavs = new ArrayList< VidEntry >( favData );
				newFavs.removeAll( prevFavData );
				out.println( "New" + "(" + newFavs.size() + "): " );
				for( VidEntry vE : newFavs )
				{
					out.println( "[Channel:" + vE.getChannel() + "]" + vE.getTitle() );
				}
				out.println();
				out.println( "---" );
				out.println();

				// Watch Later
				out.println( "[Watch Later]" );

				ArrayList< VidEntry > removedWL = new ArrayList< VidEntry >( prevWLData );
				removedWL.removeAll( wlData );
				out.println( "Removed" + "(" + removedWL.size() + "): " );
				for( VidEntry vE : removedWL )
				{
					out.println( "[Channel:" + vE.getChannel() + "]" + vE.getTitle() );
				}
				out.println();

				ArrayList< VidEntry > newWL = new ArrayList< VidEntry >( wlData );
				newWL.removeAll( prevWLData );
				out.println( "New" + "(" + newWL.size() + "): " );
				for( VidEntry vE : newWL )
				{
					out.println( "[Channel:" + vE.getChannel() + "]" + vE.getTitle() );
				}

				out.println();
				out.println( "---" );
				out.println();

				// All Playlist Items

				// Print differences between playlist items
				// NOTE: Only prints differences for playlists existing during
				// both program runs, removed and new playlist
				// titles are listed separately later on, the contents of the
				// removed ones can be viewed in the previous run
				// .txt output passed into the program in order to reach this
				// point in the first place.

				// Print new and removed videos from all playlists
				// Limit to playlists existing during both runs
				HashMap< String, ArrayList< VidEntry > > plVids = new HashMap< String, ArrayList< VidEntry > >(
						plData );
				plVids.keySet().retainAll( prevPLData.keySet() );

				for( Map.Entry< String, ArrayList< VidEntry > > entry : plVids.entrySet() )
				{
					String plTitle = entry.getKey();
					ArrayList< VidEntry > vidList = new ArrayList< VidEntry >( entry.getValue() );

					vidList.removeAll( plData.get( plTitle ) );

					out.println( "[Playlist]" + plTitle );

					out.println( "Removed" + "(" + vidList.size() + "): " );
					for( VidEntry vE : vidList )
					{
						out.println( "[Channel:" + vE.getChannel() + "]" + vE.getTitle() );
					}

					out.println();

					vidList = new ArrayList< VidEntry >( entry.getValue() );
					vidList.removeAll( prevPLData.get( plTitle ) );

					out.println( "New" + "(" + vidList.size() + "): " );
					for( VidEntry vE : vidList )
					{
						out.println( "[Channel:" + vE.getChannel() + "]" + vE.getTitle() );
					}
					out.println();
					out.println( "---" );
					out.println();
				}

				// Subs
				out.println( "[Subscriptions]" );
				ArrayList< String > removedSubs = new ArrayList< String >( prevSubNames );
				removedSubs.removeAll( subNames );
				out.println( "Removed" + "(" + removedSubs.size() + "): " );
				for( String s : removedSubs )
				{
					out.println( s );
				}
				out.println();

				ArrayList< String > newSubs = new ArrayList< String >( subNames );
				newSubs.removeAll( prevSubNames );
				out.println( "New" + "(" + newSubs.size() + "): " );
				for( String s : newSubs )
				{
					out.println( s );
				}
				out.println();
				out.println( "---" );
				out.println();

				// Playlists
				out.println( "[Playlists]" );

				HashSet< String > removedPLs = new HashSet< String >( prevPLData.keySet() );
				removedPLs.removeAll( plData.keySet() );

				out.println( "Removed" + "(" + removedPLs.size() + "): " );
				for( String s : removedPLs )
				{
					out.println( s );
				}
				out.println();

				HashSet< String > newPLs = new HashSet< String >( plData.keySet() );
				newPLs.removeAll( prevPLData.keySet() );

				out.println( "New" + "(" + newPLs.size() + "): " );
				for( String s : newPLs )
				{
					out.println( s );
				}
			}

			// Close file
			out.close();
			
			System.out.println( "Data Saved." );
			System.out.println();
			
			//Print completion message
			System.out.println( "Program run complete." );
			System.out.println( "Output stored in file: \"" + outputFileName + "\"" );
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

	static void printPlaylist( String plID, String title )
	{
		try
		{
			System.out.println( title + ":" );

			YouTube.PlaylistItems.List plItemsRequest = client.playlistItems().list( "snippet" );

			plItemsRequest.setMaxResults( Long.valueOf( 3 ) );

			plItemsRequest.setPlaylistId( plID );
			PlaylistItemListResponse plItemsResult = plItemsRequest.execute();

			List< PlaylistItem > plItemsList = plItemsResult.getItems();

			for( PlaylistItem plI : plItemsList )
			{
				// Print video title
				System.out.print( plI.getSnippet().getTitle() );

				String vId = plI.getSnippet().getResourceId().getVideoId();

				// Print video uploader
				YouTube.Videos.List vidRequest = client.videos().list( "snippet" );
				vidRequest.setId( vId );
				vidRequest.setMaxResults( Long.valueOf( 3 ) );

				VideoListResponse vidResult = vidRequest.execute();

				List< Video > vidList = vidResult.getItems();

				System.out.println( " [Uploaded by \""
						+ vidList.get( 0 ).getSnippet().getChannelTitle() + "\"]" );
			}
			System.out.println();
		}
		catch( IOException e )
		{
			System.err.println( e.getMessage() );
		}
	}
}

class VidEntry
{
	public VidEntry( String t, String c )
	{
		this.title = t;
		this.channel = c;
	}

	private String title;
	private String channel;

	public String getTitle()
	{
		return title;
	}

	public String getChannel()
	{
		return channel;
	}

	public boolean equals( Object o )
	{
		if( o == null )
			return false;
		if( o == this )
			return true;
		if( !( o instanceof VidEntry ) )
			return false;

		VidEntry rhs = ( VidEntry ) o;

		return new EqualsBuilder().append( title, rhs.title ).append( channel, rhs.channel )
				.isEquals();
	}

	public int hashCode()
	{
		return new HashCodeBuilder( 97, 13 ).append( title ).append( channel ).toHashCode();
	}
}