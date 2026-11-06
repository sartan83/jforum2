package net.jforum.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Former <code>jforum_ranks</code> table.
 */
@Document(collection = RankDocument.COLLECTION)
public class RankDocument
{
	public static final String COLLECTION = "ranks";

	@Id
	private int id;
	private String rankTitle;

	@Indexed
	private Integer rankMin;
	private Integer rankSpecial;
	private String rankImage;

	public int getId()
	{
		return this.id;
	}

	public void setId(int id)
	{
		this.id = id;
	}

	public String getRankTitle()
	{
		return this.rankTitle;
	}

	public void setRankTitle(String rankTitle)
	{
		this.rankTitle = rankTitle;
	}

	public Integer getRankMin()
	{
		return this.rankMin;
	}

	public void setRankMin(Integer rankMin)
	{
		this.rankMin = rankMin;
	}

	public Integer getRankSpecial()
	{
		return this.rankSpecial;
	}

	public void setRankSpecial(Integer rankSpecial)
	{
		this.rankSpecial = rankSpecial;
	}

	public String getRankImage()
	{
		return this.rankImage;
	}

	public void setRankImage(String rankImage)
	{
		this.rankImage = rankImage;
	}
}
